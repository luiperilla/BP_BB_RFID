package com.agp.uhf.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.agp.uhf.R;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import android.widget.ArrayAdapter;
/** BARCODE READER LIBS **/
import com.agp.uhf.tools.BarcodeManager;
import com.rscja.barcode.BarcodeDecoder;
import com.rscja.barcode.BarcodeFactory;
import com.rscja.deviceapi.entity.BarcodeEntity;

public class AsociarFragment extends Fragment {

    private EditText OP, TAG_RFID;
    private Spinner spinner_CLV_MODEL;
    private Button buttonSend;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_asociar, container, false);

        OP = view.findViewById(R.id.OP);
        TAG_RFID = view.findViewById(R.id.TAG_RFID);
        spinner_CLV_MODEL = view.findViewById(R.id.spinner_CLV_MODEL);
        buttonSend = view.findViewById(R.id.buttonSend);

        BarcodeDecoder decoder = BarcodeManager.getInstance(getContext());
        decoder.setDecodeCallback(new BarcodeDecoder.DecodeCallback() {
            @Override
            public void onDecodeComplete(BarcodeEntity barcodeEntity) {
                if (barcodeEntity.getResultCode() == BarcodeDecoder.DECODE_SUCCESS) {
                    requireActivity().runOnUiThread(() -> {
                        if (TAG_RFID.hasFocus()) {
                            TAG_RFID.setText(barcodeEntity.getBarcodeData());
                        } else if (OP.hasFocus()) {
                            OP.setText(barcodeEntity.getBarcodeData());
                        }
                    });
                }
            }
    });


        buttonSend.setOnClickListener(v -> {
            String op = OP.getText().toString();
            String modelo = spinner_CLV_MODEL.getSelectedItem() != null ? spinner_CLV_MODEL.getSelectedItem().toString() : "";
            String epc = TAG_RFID.getText().toString();

            if (op.isEmpty() || modelo.isEmpty() || epc.isEmpty()) {
                Toast.makeText(getContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show();
            } else {
                enviarDatosAFirebase(op, modelo, epc);
            }


        });

        spinner_CLV_MODEL.setOnTouchListener((v, event) -> {
            String orden = OP.getText().toString().trim();
            if (!orden.isEmpty()) {
                consultarModelosPorOrden(orden);
            } else {
                Toast.makeText(getContext(), "Ingrese una OP para consultar modelos", Toast.LENGTH_SHORT).show();
            }
            return false;
        });

        return view;
    }

    private void consultarModelosPorOrden(String orden) {
        OkHttpClient client = new OkHttpClient();
        String url = "http://172.16.60.189:8260/modelo?orden=" + orden;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("API Error", "Error en la conexión con la API: " + e.getMessage());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Error al conectar con la API", Toast.LENGTH_SHORT).show()
                    );
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    Log.d("API Response", "Respuesta de la API: " + json);
                    try {
                        JSONArray jsonArray = new JSONArray(json);
                        ArrayList<String> modelos = new ArrayList<>();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            modelos.add(jsonArray.getString(i));
                        }

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                                        android.R.layout.simple_spinner_item, modelos);
                                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                spinner_CLV_MODEL.setAdapter(adapter);
                            });
                        }
                    } catch (JSONException e) {
                        Log.e("JSON Error", "Error al parsear la respuesta JSON: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    Log.e("API Error", "Respuesta de la API no exitosa: " + response.message());
                }
            }
        });
    }

    private void enviarDatosAFirebase(String op, String modelo, String epc) {
        OkHttpClient client = new OkHttpClient();

        JSONObject json = new JSONObject();
        try {
            json.put("OP", op);
            json.put("CLV_MODELO", modelo);
            json.put("EPC", epc);
        } catch (JSONException e) {
            Log.e("JSON Error", "Error al crear el JSON para la API: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json.toString(), JSON);
        String url = "http://172.16.60.189:8260/guardar_datos";

        Request request = new Request.Builder().url(url).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("API Error", "Error al conectar con la API para guardar datos: " + e.getMessage());
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Error al conectar con la API", Toast.LENGTH_LONG).show()
                    );
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseData = response.body().string();
                Log.d("API Response", "Respuesta de la API al guardar datos: " + responseData);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(getContext(), "Datos guardados correctamente", Toast.LENGTH_SHORT).show();
                            limpiarSpinnerYTag();  // ✅ Limpia solo el Spinner y el campo TAG
                        } else {
                            String mensajeError = "Error al guardar datos"; try { org.json.JSONObject errJson = new org.json.JSONObject(responseData); if (errJson.has("error")) { mensajeError = errJson.getString("error"); } } catch (Exception ex) { } Toast.makeText(getContext(), mensajeError, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private void limpiarSpinnerYTag() {
        // Limpiar TAG_RFID
        TAG_RFID.setText("");

        // Vaciar el Spinner de modelos
        ArrayAdapter<String> emptyAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, new ArrayList<>());
        emptyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_CLV_MODEL.setAdapter(emptyAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().getWindow().getDecorView().setFocusableInTouchMode(true);
        requireActivity().getWindow().getDecorView().requestFocus();
        requireActivity().getWindow().getDecorView().setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == 139) {
                BarcodeDecoder decoder = BarcodeManager.getInstance(getContext());
                decoder.startScan();
                return true;  // ya lo manejamos
            }
            return false;
        });
    }

}

package com.agp.uhf.fragment;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import com.agp.uhf.tools.EpcHolder;

import com.rscja.barcode.BarcodeDecoder;
import com.rscja.deviceapi.entity.BarcodeEntity;
import com.agp.uhf.tools.BarcodeManager;


import com.agp.uhf.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class ConsultarFragment extends Fragment {

    private EditText etOpConsulta;
    private Button btnConsultar;
    private Spinner spinnerConsultaTipo;
    private Button btnLiberar;
    private TableLayout tblResultado;
    private String tipoConsulta = "OP";  // Variable para determinar si la consulta es por OP o EPC
    private String epcSeleccionado = "";  // Guarda el EPC seleccionado
    private TextView epcSeleccionadoTextView = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_consultar, container, false);

        etOpConsulta = view.findViewById(R.id.et_op_consulta);
        btnConsultar = view.findViewById(R.id.btn_consultar);
        tblResultado = view.findViewById(R.id.tbl_resultado);
        spinnerConsultaTipo = view.findViewById(R.id.spinner_consulta_tipo);
        btnLiberar = view.findViewById(R.id.btnLiberar);

        // Configurar el Spinner para seleccionar el tipo de consulta
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.consulta_tipos, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerConsultaTipo.setAdapter(adapter);

        // Establecer el tipo de consulta basado en el Spinner
        spinnerConsultaTipo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                tipoConsulta = (String) parentView.getItemAtPosition(position);  // "OP" o "EPC"
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                tipoConsulta = "OP";  // Default: OP
            }
        });

        // Acción para el botón de consulta
        btnConsultar.setOnClickListener(v -> {
            String input = etOpConsulta.getText().toString().trim();
            String tipoSeleccionado = spinnerConsultaTipo.getSelectedItem().toString();  // <-- siempre actualizado

            if (!input.isEmpty()) {
                if ("OP".equalsIgnoreCase(tipoSeleccionado)) {
                    consultarPorOP(input);
                } else {
                    consultarPorEPC(input);
                }
            } else {
                Toast.makeText(getContext(), "Ingrese un valor para consultar", Toast.LENGTH_SHORT).show();
            }
        });

        // Acción para el botón de liberar
        btnLiberar.setOnClickListener(v -> {
            String input = etOpConsulta.getText().toString().trim();
            String tipoSeleccionado = spinnerConsultaTipo.getSelectedItem().toString();  // "OP" o "EPC"

            if (!input.isEmpty()) {
                new AlertDialog.Builder(getContext())
                        .setTitle("Confirmación")
                        .setMessage("¿Está seguro de eliminar el registro?")
                        .setPositiveButton("Sí", (dialog, which) -> {
                            if ("OP".equalsIgnoreCase(tipoSeleccionado)) {
                                eliminarRegistrosPorOP(input);
                            } else if ("EPC".equalsIgnoreCase(tipoSeleccionado)) {
                                eliminarRegistrosPorEPC(input);
                            } else {
                                Toast.makeText(getContext(), "Tipo de consulta no válido", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                        .show();
            } else {
                Toast.makeText(getContext(), "Ingrese un valor para eliminar", Toast.LENGTH_SHORT).show();
            }
        });

        // Activar escáner para entrada automática si está en modo EPC
        BarcodeDecoder decoder = BarcodeManager.getInstance(getContext());
        decoder.setDecodeCallback(new BarcodeDecoder.DecodeCallback() {
            @Override
            public void onDecodeComplete(BarcodeEntity barcodeEntity) {
                if (barcodeEntity.getResultCode() == BarcodeDecoder.DECODE_SUCCESS) {
                    requireActivity().runOnUiThread(() -> {
                        if (etOpConsulta != null && etOpConsulta.hasFocus()) {
                            etOpConsulta.setText(barcodeEntity.getBarcodeData());
                        }
                    });
                }
            }
        });



        return view;
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
                return true;
            }
            return false;
        });
    }

    // Eliminar registros por OP
    private void eliminarRegistrosPorOP(String op) {
        OkHttpClient client = new OkHttpClient();
        String url = "http://172.16.60.189:8260/eliminar_datos?orden=" + op;
        Request request = new Request.Builder().url(url).delete().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Error al eliminar: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                requireActivity().runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(getContext(), "Registros eliminados", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Error del servidor: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // Consultar por OP
    private void consultarPorOP(String op) {
        OkHttpClient client = new OkHttpClient();
        String url = "http://172.16.60.189:8260/modelos_y_epc?orden=" + op;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Error de conexión con la API", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String json = response.body().string();

                requireActivity().runOnUiThread(() -> {
                    try {
                        JSONArray data = new JSONArray(json);
                        tblResultado.removeAllViews(); // Limpiamos la tabla antes de agregar las nuevas filas
                        tblResultado.setBackgroundColor(Color.WHITE);
                        tblResultado.setPadding(16, 16, 16, 16);

                        if (data.length() > 0) {
                            // Crear fila de cabecera
                            TableRow titleRow = new TableRow(getContext());
                            TextView titleClvModel = new TextView(getContext());
                            titleClvModel.setText("Clave Modelo");
                            titleClvModel.setPadding(16, 16, 16, 16);
                            titleClvModel.setTextSize(18);
                            titleClvModel.setGravity(Gravity.CENTER);

                            TextView titleEPC = new TextView(getContext());
                            titleEPC.setText("EPC");
                            titleEPC.setPadding(16, 16, 16, 16);
                            titleEPC.setTextSize(18);
                            titleEPC.setGravity(Gravity.CENTER);

                            titleRow.addView(titleClvModel);
                            titleRow.addView(titleEPC);
                            tblResultado.addView(titleRow);

                            // Agregar filas de datos
                            for (int i = 0; i < data.length(); i++) {
                                JSONArray rowData = data.getJSONArray(i);
                                String modelo = rowData.getString(0);  // Clave Modelo
                                String epc = rowData.getString(1);     // EPC

                                TableRow row = new TableRow(getContext());
                                TextView tvModelo = new TextView(getContext());
                                tvModelo.setText(modelo);
                                tvModelo.setPadding(16, 16, 16, 16);
                                tvModelo.setTextSize(18);
                                tvModelo.setGravity(Gravity.CENTER);

                                TextView tvEpc = new TextView(getContext());
                                tvEpc.setText(epc);
                                tvEpc.setPadding(16, 16, 16, 16);
                                tvEpc.setTextSize(18);
                                tvEpc.setGravity(Gravity.CENTER);

                                // Si el EPC está vacío, cambiar el color de la Clave Modelo a rojo
                                if (epc.isEmpty()) {
                                    tvModelo.setTextColor(Color.RED);  // Cambiar color del texto a rojo si EPC está vacío
                                }

                                // Hacemos que la fila de EPC sea seleccionable
                                tvEpc.setOnClickListener(v -> {
                                    // Si se selecciona una celda EPC diferente, deselecciona la anterior
                                    if (epcSeleccionadoTextView != null) {
                                        epcSeleccionadoTextView.setBackgroundResource(R.drawable.cell_border);  // Vuelve a su color original
                                    }

                                    // Cambiar color de fondo al seleccionar la celda EPC
                                    v.setBackgroundColor(Color.LTGRAY);  // Cambiar color de la celda seleccionada
                                    epcSeleccionadoTextView = (TextView) v;  // Actualiza la celda EPC seleccionada
                                    epcSeleccionado = epc;  // Guarda el EPC seleccionado

                                    // GUARDAR EPC EN EL HOLDER
                                    EpcHolder.epcSeleccionado = epc;

                                    // Mostrar solo el EPC seleccionado en el Toast
                                    Toast.makeText(getContext(), "EPC seleccionado: " + epcSeleccionado, Toast.LENGTH_SHORT).show();
                                });

                                row.addView(tvModelo);
                                row.addView(tvEpc);

                                tblResultado.addView(row);
                            }
                        } else {
                            // Si no hay datos, mostrar mensaje
                            TextView noResultText = new TextView(getContext());
                            noResultText.setText("No se encontró información.");
                            tblResultado.addView(noResultText);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    // Consultar por EPC
    private void consultarPorEPC(String epc) {
        OkHttpClient client = new OkHttpClient();
        String url = "http://172.16.60.189:8260/consulta_por_epc?epc=" + epc;  // URL de la API para EPC

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Error de conexión con la API", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String json = response.body().string();

                requireActivity().runOnUiThread(() -> {
                    try {
                        // Procesar el JSON recibido
                        JSONObject data = new JSONObject(json);

                        // Obtener los valores de OP y CLV_MODEL
                        String op = data.getString("OP");
                        String clvModel = data.getString("CLV_MODEL");

                        // Limpiar la tabla antes de agregar los nuevos resultados
                        tblResultado.removeAllViews();
                        tblResultado.setBackgroundColor(Color.WHITE);
                        tblResultado.setPadding(16, 16, 16, 16);

                        // Crear fila de cabecera
                        TableRow titleRow = new TableRow(getContext());
                        TextView titleOP = new TextView(getContext());
                        titleOP.setText("OP");
                        titleOP.setPadding(16, 16, 16, 16);
                        titleOP.setTextSize(18);
                        titleOP.setGravity(Gravity.CENTER);

                        TextView titleClvModel = new TextView(getContext());
                        titleClvModel.setText("Clave Modelo");
                        titleClvModel.setPadding(16, 16, 16, 16);
                        titleClvModel.setTextSize(18);
                        titleClvModel.setGravity(Gravity.CENTER);

                        titleRow.addView(titleOP);
                        titleRow.addView(titleClvModel);
                        tblResultado.addView(titleRow);

                        // Crear fila de resultados
                        TableRow row = new TableRow(getContext());
                        TextView tvClvModel = new TextView(getContext());
                        tvClvModel.setText(op);
                        tvClvModel.setPadding(16, 16, 16, 16);
                        tvClvModel.setTextSize(18);
                        tvClvModel.setGravity(Gravity.CENTER);

                        TextView tvEpc = new TextView(getContext());
                        tvEpc.setText(clvModel);
                        tvEpc.setPadding(16, 16, 16, 16);
                        tvEpc.setTextSize(18);
                        tvEpc.setGravity(Gravity.CENTER);

                        row.addView(tvClvModel);
                        row.addView(tvEpc);
                        tblResultado.addView(row);

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), "Error al procesar los datos", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void eliminarRegistrosPorEPC(String epc) {
        OkHttpClient client = new OkHttpClient();
        String url = "http://172.16.60.189:8260/eliminar_por_epc?epc=" + epc;

        Request request = new Request.Builder().url(url).delete().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Error al eliminar por EPC: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                requireActivity().runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(getContext(), "Registro eliminado por EPC", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Error del servidor: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }


}

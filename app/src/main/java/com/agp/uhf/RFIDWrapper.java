package com.agp.uhf;

import android.util.Log;

import com.rscja.deviceapi.entity.FastInventoryEntity;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;
import com.rscja.deviceapi.entity.InventoryModeEntity;
import com.rscja.deviceapi.entity.Gen2Entity;



public class RFIDWrapper {

    private static final String TAG = "RFIDWrapper";

    private RFIDWithUHFUART realReader = null;
    private boolean isSimulated = true;

    public void init() {
        try {
            realReader = RFIDWithUHFUART.getInstance();
            isSimulated = realReader == null;
            if (isSimulated) {
                Log.w(TAG, "RFIDWithUHFUART.getInstance() devolvió null. Modo simulación activado.");
            }
        } catch (Exception e) {
            Log.w(TAG, "Fallo en getInstance(). Modo simulación activado.", e);
            realReader = null;
            isSimulated = true;
        }
    }

    public boolean isSimulated() {
        return isSimulated;
    }

    public int getFrequencyMode() {
        if (isSimulated) {
            Log.d(TAG, "[Simulado] getFrequencyMode -> 0");
            return 0;
        }
        return realReader != null ? realReader.getFrequencyMode() : -1;
    }

    public void setInventoryCallback(IUHFInventoryCallback callback) {
        if (isSimulated) {
            Log.d(TAG, "[Simulado] setInventoryCallback ignorado.");
        } else if (realReader != null) {
            realReader.setInventoryCallback(callback);
        }
    }

    public UHFTAGInfo readTagFromBuffer() {
        if (isSimulated) {
            Log.d(TAG, "[Simulado] readTagFromBuffer devuelve TAG de prueba.");
            UHFTAGInfo fakeTag = new UHFTAGInfo();
            fakeTag.setEPC("FAKE1234567890");
            fakeTag.setRssi("-45");
            fakeTag.setPhase(90);
            return fakeTag;
        }

        return realReader != null ? realReader.readTagFromBuffer() : null;
    }

    public boolean stopLocation() {
        if (isSimulated) {
            Log.d(TAG, "[Simulado] stopLocation ignorado");
            return true;
        }
        return realReader != null && realReader.stopLocation();
    }

    public int getRFLink() {
        if (isSimulated) {
            Log.d(TAG, "[Simulado] getRFLink -> 1");
            return 1; // valor simulado
        }
        return realReader != null ? realReader.getRFLink() : -1;
    }

    public int getPower() {
        if (isSimulated) {
            Log.d(TAG, "[Simulado] getPower -> 1");
            return 1; // valor simulado
        }
        return realReader != null ? realReader.getPower() : -1;
    }

    public InventoryModeEntity getEPCAndTIDUserMode() {
        if (isSimulated) {
            Log.d(TAG, "[Simulado] getEPCAndTIDUserMode -> modo: 2, offset: 4, length: 6");

            InventoryModeEntity.Builder builder = new InventoryModeEntity.Builder();
            builder.setMode((char) 2);           // Simula modo de lectura EPC + TID
            builder.setUserOffset((char) 4);     // Offset simulado
            builder.setUserLength((char) 6);     // Longitud de usuario

            return builder.build();
        }

        return realReader != null ? realReader.getEPCAndTIDUserMode() : null;
    }

    public Gen2Entity getGen2() {
        if (isSimulated) {
            Log.d(TAG, "[Simulado] getGen2 devuelve entidad simulada");

            Gen2Entity entity = new Gen2Entity();
            entity.setQuerySession(1); // por ejemplo: sesión 1
            entity.setQueryTarget(0);  // por ejemplo: A

            return entity;
        }

        return realReader != null ? realReader.getGen2() : null;
    }

    public int getFastInventoryMode() {
        if (isSimulated) {
            Log.d(TAG, "[Simulado] getFastInventoryMode -> devuelve 1 (activo)");
            return 1; // Simula que está activado
        }
        return realReader != null ? realReader.getFastInventoryMode().getCr() : -1;
    }

    public String readData(String epc, int memBank, int addr, int len) {
        if (isSimulated) {
            Log.d(TAG, "[Simulado] readData -> devuelve datos falsos");
            return "FAKE_DATA_" + addr + "_" + len;
        }

        return realReader != null ? realReader.readData(epc, memBank, addr, len) : null;
    }

    public String readData(String accessPassword, int filterBank, int filterPtr, int filterCnt, String filterData, int memBank, int addr, int len) {
        if (isSimulated) {
            Log.d(TAG, "[Simulado] readData extendido -> devuelve datos filtrados falsos");
            return "SIM_EXT_" + filterData + "_" + memBank + "_" + addr + "_" + len;
        }

        return realReader != null ? realReader.readData(accessPassword, filterBank, filterPtr, filterCnt, filterData, memBank, addr, len) : null;
    }

}

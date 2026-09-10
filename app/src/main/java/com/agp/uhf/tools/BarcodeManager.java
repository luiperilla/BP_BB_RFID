// BarcodeManager.java
package com.agp.uhf.tools;

import android.content.Context;

import com.rscja.barcode.BarcodeDecoder;
import com.rscja.barcode.BarcodeFactory;

public class BarcodeManager {
    private static BarcodeDecoder barcodeDecoder = null;

    public static BarcodeDecoder getInstance(Context context) {
        if (barcodeDecoder == null) {
            barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
            barcodeDecoder.open(context);
        }
        return barcodeDecoder;
    }

    public static void close() {
        if (barcodeDecoder != null) {
            barcodeDecoder.close();
            barcodeDecoder = null;
        }
    }
}

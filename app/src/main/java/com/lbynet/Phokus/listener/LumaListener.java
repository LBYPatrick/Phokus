package com.lbynet.Phokus.listener;

import com.lbynet.Phokus.utils.MathTools;
import com.lbynet.Phokus.utils.SAL;
import java.util.Arrays;

public abstract class LumaListener extends EventListener {
    public void onDataUpdate(int [] bucket) {
        SAL.print("Luma: " + Arrays.toString(bucket) + "\t Total:" + MathTools.sumOf(bucket));
    }
}

package com.lbynet.phokus.deprecated.listener;

import com.lbynet.phokus.template.EventListener;
import com.lbynet.phokus.utils.MathTools;
import com.lbynet.phokus.utils.SAL;
import java.util.Arrays;

public abstract class LumaListener extends EventListener {
    public void onDataUpdate(int [] bucket) {
        SAL.print("Luma: " + Arrays.toString(bucket) + "\t Total:" + MathTools.sumOf(bucket));
    }
}

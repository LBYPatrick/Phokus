package com.lbynet.phokus.utils;

import java.util.concurrent.locks.Condition;

public class Threading {

    public static int condAwait(Condition condition) {
        try {
            condition.await();
            return 0;
        } catch (InterruptedException e) {

            SAL.print(e,false);
            return -1;
        }
    }

}

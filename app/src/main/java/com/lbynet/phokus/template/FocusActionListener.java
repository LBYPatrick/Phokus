package com.lbynet.phokus.template;

import com.lbynet.phokus.camera.FocusAction;

public interface FocusActionListener {

    void onFocusEnd(FocusAction.FocusActionResult res);

    void onFocusBusy(FocusAction.FocusActionRequest req);

}

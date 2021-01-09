package com.lbynet.Phokus.frames;

public abstract class EventListener {

    //public abstract boolean onEventCreated(String extra);
    public boolean onEventBegan(String extra) {return true;};
    public boolean onEvenFinished(boolean isSuccess, String extra) {return true;};
}

package com.lbynet.Phokus.frames;

public abstract class EventListener {

    //public abstract boolean onEventCreated(String extra);
    public abstract boolean onEventBegan(String extra);
    public abstract boolean onEvenFinished(boolean isSuccess, String extra);
}

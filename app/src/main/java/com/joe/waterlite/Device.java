package com.joe.waterlite;

import org.json.JSONObject;

final class Device {
    final String id;
    String name;
    boolean pinned;
    boolean running;
    boolean restored;
    int idleChecks;
    int status = -1; // Unknown until an authoritative status response arrives.
    long checkedAt, ownStart;
    Device(String id, String name) { this.id = id; this.name = name.isEmpty() ? "饮水机 " + id : name; }
    JSONObject json() throws Exception { return new JSONObject().put("id", id).put("name", name).put("pinned", pinned).put("ownStart", ownStart).put("running", running); }
    static Device from(JSONObject o) {
        Device d = new Device(o.optString("id"), o.optString("name"));
        d.pinned = o.optBoolean("pinned"); d.ownStart = o.optLong("ownStart"); d.running=o.optBoolean("running",d.ownStart>0); d.restored=d.running; return d;
    }
    boolean canEnd() { return running; }
    void acceptStatus(int value) {
        acceptStatus(value,false);
    }
    void acceptStatus(int value,boolean resumed) {
        status=value; checkedAt=System.currentTimeMillis();
        if(value!=99){restored=false;running=true;idleChecks=0;}
        else if(!running || ((resumed || restored) && (ownStart==0 || checkedAt-ownStart>=7500)) || ++idleChecks>=5){running=false;ownStart=0;idleChecks=0;restored=false;}
    }
}

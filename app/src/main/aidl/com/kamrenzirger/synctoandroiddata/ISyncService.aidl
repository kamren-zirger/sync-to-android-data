package com.kamrenzirger.synctoandroiddata;

import java.util.List;

interface ISyncService {
    int runCommand(String command);
    List<String> runCommandWithOutput(String command);
    void destroy();
}

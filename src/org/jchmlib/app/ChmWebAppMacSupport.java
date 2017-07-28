package org.jchmlib.app;

import com.apple.eawt.AppEvent.OpenFilesEvent;
import com.apple.eawt.Application;
import com.apple.eawt.OpenFilesHandler;
import java.io.File;

@SuppressWarnings("unused")
class ChmWebAppMacSupport implements ChmWebAppSpecificPlatform, OpenFilesHandler {

    private ChmWebApp app;

    public ChmWebAppMacSupport() {
    }

    @Override
    public void initialize(ChmWebApp app) {
        this.app = app;
        Application a = Application.getApplication();
        a.setOpenFileHandler(this);
    }

    @Override
    public void openFiles(OpenFilesEvent openFilesEvent) {
        for (Object obj : openFilesEvent.getFiles()) {
            File file = (File) obj;
            if (file != null) {
                this.app.openFile(file);
            }
        }
    }
}

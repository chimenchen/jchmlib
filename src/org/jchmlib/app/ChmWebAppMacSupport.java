package org.jchmlib.app;

import com.apple.eawt.AppEvent.OpenFilesEvent;
import com.apple.eawt.Application;
import com.apple.eawt.OpenFilesHandler;
import java.io.File;

class ChmWebAppMacSupport implements ChmWebAppSpecificPlatform {

    public ChmWebAppMacSupport() {
    }

    public void initialize(ChmWebApp app) {
        Application a = Application.getApplication();
        a.setOpenFileHandler(new OpenFilesHandler() {
            @Override
            public void openFiles(OpenFilesEvent e) {
                for (Object obj : e.getFiles()) {
                    File file = (File) obj;
                    if (file != null) {
                        app.openFile(file);
                    }
                }
            }
        });
    }

}

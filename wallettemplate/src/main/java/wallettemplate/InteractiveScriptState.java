package wallettemplate;

import com.aquafx_project.AquaFx;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.aerofx.AeroFX;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import wallettemplate.controls.NotificationBarPane;
import wallettemplate.utils.GuiUtils;
import wallettemplate.utils.TextFieldValidator;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URL;

import static wallettemplate.utils.GuiUtils.*;
import static wallettemplate.utils.GuiUtils.blurIn;
import static wallettemplate.utils.GuiUtils.fadeOutAndRemove;

public class InteractiveScriptState extends Application {
    private static final String APP_NAME = "Script Debugger" ;
    private Stage mainWindow;
    private static InteractiveScriptState instance;
    private StackPane uiStack;
    private Pane mainUI;
    private InteractiveScriptStateController controller;
    private NotificationBarPane notificationBar;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.mainWindow = primaryStage;
        instance = this;
        // Show the crash dialog for any exceptions that we don't handle and that hit the main loop.
        GuiUtils.handleCrashesOnThisThread();
        setUserAgentStylesheet(STYLESHEET_MODENA);
        AquaFx.style();

        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            // We could match the Mac Aqua style here, except that (a) Modena doesn't look that bad, and (b)
            // the date picker widget is kinda broken in AquaFx and I can't be bothered fixing it.
            // AquaFx.style();
        }

        // Load the GUI. The InteractiveScriptStateController class will be automagically created and wired up.
        URL location = getClass().getResource("interactive_sscript_debugger.fxml");
        FXMLLoader loader = new FXMLLoader(location);
        mainUI = loader.load();
        controller = loader.getController();
        // Configure the window with a StackPane so we can overlay things on top of the main UI, and a
        // NotificationBarPane so we can slide messages and progress bars in from the bottom. Note that
        // ordering of the construction and connection matters here, otherwise we get (harmless) CSS error
        // spew to the logs.
        notificationBar = new NotificationBarPane(mainUI);
        mainWindow.setTitle(APP_NAME);
        mainWindow.setMaximized(true);
        uiStack = new StackPane();
        Scene scene = new Scene(uiStack);
        TextFieldValidator.configureScene(scene);   // Add CSS that we need.
        scene.getStylesheets().add(getClass().getResource("script_debugger.css").toString());
        uiStack.getChildren().add(notificationBar);
        mainWindow.setScene(scene);

        // Make log output concise.
        BriefLogFormatter.init();
        // Tell bitcoinj to run event handlers on the JavaFX UI thread. This keeps things simple and means
        // we cannot forget to switch threads when adding event handlers. Unfortunately, the DownloadListener
        // we give to the app kit is currently an exception and runs on a library thread. It'll get fixed in
        // a future version.
        Threading.USER_THREAD = Platform::runLater;
        // Create the app kit. It won't do any heavyweight initialization until after we start it.
        mainWindow.show();

    }

    public Stage getMainWindow() {
        return mainWindow;
    }

    public void setMainWindow(Stage mainWindow) {
        this.mainWindow = mainWindow;
    }

    public static InteractiveScriptState getInstance() {
        return instance;
    }

    public static void setInstance(InteractiveScriptState instance) {
        InteractiveScriptState.instance = instance;
    }

    public StackPane getUiStack() {
        return uiStack;
    }

    public void setUiStack(StackPane uiStack) {
        this.uiStack = uiStack;
    }

    public Pane getMainUI() {
        return mainUI;
    }

    public void setMainUI(Pane mainUI) {
        this.mainUI = mainUI;
    }

    public InteractiveScriptStateController getController() {
        return controller;
    }

    public void setController(InteractiveScriptStateController controller) {
        this.controller = controller;
    }

    public NotificationBarPane getNotificationBar() {
        return notificationBar;
    }

    public void setNotificationBar(NotificationBarPane notificationBar) {
        this.notificationBar = notificationBar;
    }

    private Node stopClickPane = new Pane();

    @Nullable
    private InteractiveScriptState.OverlayUI currentOverlay;

    public class OverlayUI<T> {
        Node ui;
        public T controller;

        OverlayUI(Node ui, T controller) {
            this.ui = ui;
            this.controller = controller;
        }

        void show() {
            checkGuiThread();
            if (currentOverlay == null) {
                uiStack.getChildren().add(stopClickPane);
                uiStack.getChildren().add(ui);
                blurOut(mainUI);
                //darken(mainUI);
             //   fadeIn(ui);
            //    zoomIn(ui);
            } else {
                // Do a quick transition between the current overlay and the next.
                // Bug here: we don't pay attention to changes in outsideClickDismisses.
                explodeOut(currentOverlay.ui);
                fadeOutAndRemove(uiStack, currentOverlay.ui);
                uiStack.getChildren().add(ui);
                ui.setOpacity(0.0);
       //         fadeIn(ui, 100);
       //         zoomIn(ui, 100);
            }
            currentOverlay = this;
        }

        public void outsideClickDismisses() {
            stopClickPane.setOnMouseClicked((ev) -> done());
        }

        void done() {
            checkGuiThread();
            if (ui == null) return;  // In the middle of being dismissed and got an extra click.
            explodeOut(ui);
            fadeOutAndRemove(uiStack, ui, stopClickPane);
            blurIn(mainUI);
            //undark(mainUI);
            this.ui = null;
            this.controller = null;
            currentOverlay = null;
        }
    }


    /** Loads the FXML file with the given name, blurs out the main UI and puts this one on top. */
    public <T> InteractiveScriptState.OverlayUI<T> overlayUI(String name) {
        try {
            checkGuiThread();
            // Load the UI from disk.
            URL location = GuiUtils.getResource(name);
            FXMLLoader loader = new FXMLLoader(location);
            Pane ui = loader.load();
            T controller = loader.getController();
            InteractiveScriptState.OverlayUI<T> pair = new InteractiveScriptState.OverlayUI<T>(ui, controller);
            // Auto-magically set the overlayUI member, if it's there.
            try {
                if (controller != null)
                    controller.getClass().getField("overlayUI").set(controller, pair);
            } catch (IllegalAccessException | NoSuchFieldException exception) {
                exception.printStackTrace();
            }
            pair.show();
            return pair;
        } catch (IOException e) {
            throw new RuntimeException(e);  // Can't happen.
        }
    }
}

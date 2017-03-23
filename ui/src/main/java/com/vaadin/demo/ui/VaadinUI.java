package com.vaadin.demo.ui;

import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Widgetset;
import com.vaadin.demo.ui.security.SecurityUtils;
import com.vaadin.server.*;
import com.vaadin.shared.communication.PushMode;
import com.vaadin.shared.ui.ui.Transport;
import com.vaadin.spring.annotation.SpringUI;
import com.vaadin.ui.*;
import com.vaadin.ui.themes.ValoTheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedList;

@SpringUI
@Theme("portal")
@Widgetset("com.vaadin.demo.ui.PatientPortalWidgetSet")
public class VaadinUI extends UI {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    private PatientView patientView;

    @Autowired
    private AnalyticsView analyticsView;

    private TabSheet tabsheet;
    private AbsoluteLayout layout = new AbsoluteLayout();

    private LinkedList<SubView> subviews = new LinkedList();

    private LayoutMode lastRenderMode;
    private Button logout;

    @Override
    protected void init(VaadinRequest vaadinRequest) {

        if(SecurityUtils.isLoggedIn()){
            showMainView();
        } else {
            showLoginView();
        }
    }

    private void showLoginView() {
        setContent(new LoginView(this::login));
    }

    private void showMainView() {
        tabsheet = new TabSheet();
        tabsheet.addStyleName("main");
        tabsheet.setSizeFull();
        tabsheet.addComponents(patientView);
        tabsheet.addComponent(analyticsView);

        tabsheet.addSelectedTabChangeListener(e -> closeAllSubViews());

        layout.addComponent(tabsheet);
        setContent(layout);

        lastRenderMode = getLayoutMode();

        logout = new Button("Logout", clickEvent -> {
           showLoginView();
        });
        logout.addStyleName(ValoTheme.BUTTON_BORDERLESS);
        logout.addStyleName("logout-button");
        logout.setIcon(FontAwesome.SIGN_OUT);
        layout.addComponent(logout, "top:0;right:0");

        // Add a resize listener so we can check if we need to redraw in a new mode
        Page.getCurrent().addBrowserWindowResizeListener(resize -> {
            LayoutMode layoutMode = getLayoutMode();
            if (!layoutMode.equals(lastRenderMode)) {
                ((MainView) tabsheet.getSelectedTab()).repaint();
                subviews.forEach(subView -> {
                    updateWidths(subView);
                    subView.repaint();
                });
                lastRenderMode = layoutMode;
            }
        });
    }



    public void showSubView(SubView subView) {
        if (!subviews.isEmpty()) {
            if (subviews.getFirst() == subView) {
                return;
            }
            layout.removeComponent(subviews.getFirst());
        }
        subviews.addFirst(subView);
        makeSubViewVisible(subView);
    }

    private void makeSubViewVisible(SubView subView) {
        updateWidths(subView);
        layout.addComponent(subView, "top:0;right:0;z-index:5;"); // magic number 5 ;-)
    }

    private void updateWidths(SubView subView) {
        if (getLayoutMode() == LayoutMode.HANDHELD) {
            // Note!! There is a bug when opening the subView in mobile 100% and moving to desktop version
            // that makes AbstractLayout leave a left: 0; into the css and the sub window is thus in the wrong position.
            subView.setWidth("100%");
        } else {
            subView.setWidth("960px");
        }
    }


    /**
     * Close Sub-View and inform main panel that sub view has been closed
     *
     * @param subView SubView to close
     */
    public void closeSubView(SubView subView) {
        closeSubView(subView, true);
    }

    /**
     * Close Sub-View and potentially inform main view of closing.
     * Not informing of closing is usually for closing a sub-sub-view
     *
     * @param subView         SubView to close
     * @param informMainPanel Inform main view of closing.
     */
    public void closeSubView(SubView subView, boolean informMainPanel) {
        if (subviews.contains(subView)) {
            // close also sub-sub views
            while (subviews.contains(subView)) {
                layout.removeComponent(subviews.removeFirst());
            }
            if (!subviews.isEmpty()) {
                makeSubViewVisible(subviews.getFirst());
            }
            if (informMainPanel)
                ((MainView) tabsheet.getSelectedTab()).subViewClose();
        }
    }

    public void closeAllSubViews() {
        while (!subviews.isEmpty()) {
            closeSubView(subviews.getFirst());
        }
    }

    public LayoutMode getLayoutMode() {
        int width = Page.getCurrent().getBrowserWindowWidth();
        if (width < 1024) {
            return LayoutMode.HANDHELD;
        } else {
            return LayoutMode.DESKTOP;
        }
    }

    public boolean login(String username, String password) {
        try {
            Authentication token = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, password));
            // Reinitialize the session to protect against session fixation attacks. This does not work
            // with websocket communication.
            VaadinService.reinitializeSession(VaadinService.getCurrentRequest());
            SecurityContextHolder.getContext().setAuthentication(token);
            // Now when the session is reinitialized, we can enable websocket communication. Or we could have just
            // used WEBSOCKET_XHR and skipped this step completely.
            getPushConfiguration().setTransport(Transport.WEBSOCKET);
            getPushConfiguration().setPushMode(PushMode.AUTOMATIC);
            // Show the main UI
            showMainView();
            return true;
        } catch (AuthenticationException ex) {
            return false;
        }
    }
}

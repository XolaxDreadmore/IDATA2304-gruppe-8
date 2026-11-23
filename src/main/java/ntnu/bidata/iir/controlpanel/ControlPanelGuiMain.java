package ntnu.bidata.iir.controlpanel;

import ntnu.bidata.iir.common.Role;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public final class ControlPanelGuiMain {
    private ControlPanelGuiMain() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { }
            showLogin();
        });
    }

    static void showLogin() {
        JTextField host = new JTextField("localhost");
        JTextField panelId = new JTextField("panel 1");
        JComboBox<Role> role = new JComboBox<>(Role.values());
        role.setSelectedItem(Role.OWNER);
        JPasswordField password = new JPasswordField();

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Broker host"));
        form.add(host);
        form.add(new JLabel("Panel id"));
        form.add(panelId);
        form.add(new JLabel("Role"));
        form.add(role);
        form.add(new JLabel("Password"));
        form.add(password);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(null, form, "SHA Control Panel - Login",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                System.exit(0);
            }
            Role selectedRole = (Role) role.getSelectedItem();
            ControlPanelGui gui = new ControlPanelGui(panelId.getText().trim(), selectedRole);
            try {
                PanelClient client = PanelClient.connect(host.getText().trim(), panelId.getText().trim(),
                        selectedRole, new String(password.getPassword()), gui);
                gui.attach(client);
                gui.setVisible(true);
                return;
            } catch (IOException e) {
                gui.dispose();
                JOptionPane.showMessageDialog(null, e.getMessage(), "Could not log in", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

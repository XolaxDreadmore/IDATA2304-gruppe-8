package ntnu.bidata.iir.controlpanel;

import javax.swing.JPanel;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import java.util.function.Predicate;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;


final class SecurityBar extends JPanel {
    private static final Color GREEN = new Color(0x2E9E5B);
    private static final Color BLUE = new Color(0x2F6FBF);
    private static final Color ORANGE = new Color(0xE08A00);
    private static final Color RED = new Color(0xD64545);

    private final JLabel stateLabel = new JLabel();
    private final JPasswordField codeField = new JPasswordField(7);
    private final Predicate<String> onArm;
    private final Predicate<String> onDisarm;

    /**
     * @param onArm called with the code, false if nothing could be sent
     * @param onDisarm called with the code, false if nothing could be sent
     */
    SecurityBar(Predicate<String> onArm, Predicate<String> onDisarm) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 2));
        this.onArm = onArm;
        this.onDisarm = onDisarm;
        setBorder(BorderFactory.createTitledBorder("House security"));
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD, 14f));
        stateLabel.setPreferredSize(new java.awt.Dimension(420, 24));

        JButton arm = new JButton("Arm");
        arm.addActionListener( e-> submit(onArm));
        JButton disarm = new JButton("Disarm");
        disarm.addActionListener(e -> submit(onDisarm));
        codeField.addActionListener(e -> submit(onDisarm));
        codeField.setToolTipText("House code");

        add(stateLabel);
        add(new JLabel("Code"));
        add(codeField);
        add(arm);
        add(disarm);
        update("DISARMED", 0);
    }

    private void submit(Predicate<String> action) {
        String code = new String(codeField.getPassword());
        codeField.setText("");
        action.test(code);
    }

    void update(String state, int secondsLeft) {
        switch (state) {
            case "ARMED" -> show("ARMED - the entrance is watched", BLUE);
            case "ENTRY" -> show("ENTRY DETECTED - enter the code within " + secondsLeft + " s", ORANGE);
            case "ALARM" -> show("ALARM - Intruder! Enter code to silence", RED);
            default -> show("DISARMED", GREEN);
        }
    }

    private void show(String text, Color color) {
        stateLabel.setText("House: " + text);
        stateLabel.setForeground(color);
    }
}

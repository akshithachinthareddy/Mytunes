package mytunes;

import javax.swing.SwingUtilities;

public class MyTunes {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new MyTunesFrame().setVisible(true);
        });
    }
}

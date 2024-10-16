package mytunes;

import javax.swing.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.util.List;

public class SongTransferHandler extends TransferHandler {
    private Database database;
    private MyTunesFrame frame;

    public SongTransferHandler(Database database, MyTunesFrame frame) {
        this.database = database;
        this.frame = frame;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }

        Transferable t = support.getTransferable();
        try {
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                String selectedPlaylist = frame.getSelectedPlaylist(); // Access the playlist from MyTunesFrame
                if (selectedPlaylist == null) {
                    frame.addSongsByDragAndDrop(files, true, null);
                } else {
                    frame.addSongsByDragAndDrop(files, true, selectedPlaylist);
                }
            } else if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String data = (String) t.getTransferData(DataFlavor.stringFlavor);
                int selectedRow = Integer.parseInt(data);
                Song song = frame.getSongFromRow(selectedRow);
                String selectedPlaylist = frame.getSelectedPlaylist();
                if (selectedPlaylist != null) {
                    database.addSongToPlaylist(song, selectedPlaylist);
                    frame.refreshPlaylistWindow(selectedPlaylist);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        int rowIndex = frame.getSongTable().getSelectedRow();
        return new StringSelection(String.valueOf(rowIndex));
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }
}

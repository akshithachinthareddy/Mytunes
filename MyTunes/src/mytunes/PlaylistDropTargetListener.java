package mytunes;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;

public class PlaylistDropTargetListener extends DropTargetAdapter {
    private MyTunesFrame frame;
    private Database database;

    public PlaylistDropTargetListener(MyTunesFrame frame, Database database) {
        this.frame = frame;
        this.database = database;
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        try {
            Transferable transferable = dtde.getTransferable();
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.rejectDrop();
                return;
            }

            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            int dropRow = frame.getPlaylistTree().getRowForLocation(dtde.getLocation().x, dtde.getLocation().y);
            TreePath path = frame.getPlaylistTree().getPathForRow(dropRow);

            if (path == null) {
                dtde.rejectDrop();
                return;
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            String playlistName = node.getUserObject().toString();

            int[] selectedRows = frame.getSongTable().getSelectedRows();
            for (int rowIndex : selectedRows) {
                Song song = frame.getSongFromRow(rowIndex);
                database.addSongToPlaylist(song, playlistName);
            }

            dtde.dropComplete(true);
            frame.refreshPlaylistWindow(playlistName);

        } catch (Exception ex) {
            dtde.rejectDrop();
            ex.printStackTrace();
        }
    }
}

package mytunes;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;  // Import this instead of java.awt.List
import java.util.ArrayList;
import javax.swing.Timer;  // Import this instead of java.util.Timer

public class MyTunesFrame extends JFrame {

    private JTable songTable;
    private DefaultTableModel tableModel;
    private JTree libraryTree;
    private JTree playlistTree;
    private DefaultTreeModel playlistTreeModel;
    private JButton playButton, stopButton, pauseButton, unpauseButton, nextButton, previousButton, shuffleButton, repeatButton;
    private JFileChooser fileChooser;
    private List<Song> songList = new ArrayList<>();
    private List<Song> currentPlaylist = new ArrayList<>();
    private int currentSongIndex = -1;
    private final MP3Player mp3Player = new MP3Player();
    private Database database;
    private boolean isShuffleEnabled = false;
    private boolean isRepeatEnabled = false;
    private JPopupMenu libraryPopupMenu;
    private JPopupMenu playlistPopupMenu;
    private JSlider volumeSlider;
    private JProgressBar progressBar;
    private JLabel elapsedTimeLabel, remainingTimeLabel;
    private Map<String, Boolean> columnVisibility;
    private Timer songTimer;
    private int songDuration;
    private int elapsedTime;

    public MyTunesFrame() {
        setTitle("MyTunes");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        database = new Database();
        songList = database.getAllSongs();

        // Initialize column visibility
        columnVisibility = new HashMap<>();
        columnVisibility.put("Title", true);
        columnVisibility.put("Artist", true);
        columnVisibility.put("Album", true);
        columnVisibility.put("Year", true);
        columnVisibility.put("Genre", true);
        columnVisibility.put("Comment", true);

        initUI();
        loadSongsToTable(); // Load songs into the table on startup
        loadPlaylistsFromDatabase(); // Load playlists into the tree on startup
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // Initialize the menu bar
        initMenuBar();

        // Create side panel with playlist and library tree
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setPreferredSize(new Dimension(150, getHeight()));

        // Library tree
        DefaultMutableTreeNode libraryRoot = new DefaultMutableTreeNode("Library");
        libraryTree = new JTree(libraryRoot);
        libraryTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) libraryTree.getLastSelectedPathComponent();
                if (selectedNode != null && selectedNode.getUserObject().equals("Library")) {
                    showLibrary(); // Show the library when "Library" node is selected
                    playlistTree.clearSelection(); // Clear playlist tree selection
                    currentPlaylist = songList; // Set the current playlist to the library songs
                }
            }
        });
        sidePanel.add(new JScrollPane(libraryTree));

        // Playlist tree
        DefaultMutableTreeNode playlistRoot = new DefaultMutableTreeNode("Playlist");
        playlistTreeModel = new DefaultTreeModel(playlistRoot);
        playlistTree = new JTree(playlistTreeModel);
        playlistTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) playlistTree.getLastSelectedPathComponent();
                if (selectedNode != null) {
                    if (selectedNode.isRoot()) {
                        showPlaylists(); // Show playlists when "Playlist" node is selected
                        libraryTree.clearSelection(); // Clear library tree selection
                    } else {
                        loadSelectedPlaylist(); // Load the selected playlist
                        libraryTree.clearSelection(); // Clear library tree selection
                    }
                }
            }
        });

        // Enable drag and drop for playlists
        new DropTarget(playlistTree, new PlaylistDropTargetListener(this, database));

        // Add right-click functionality to open playlists in new window
        playlistTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = playlistTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        playlistTree.setSelectionPath(path);
                        showPlaylistContextMenu(e);
                    }
                }
            }
        });

        sidePanel.add(new JScrollPane(playlistTree));
        add(sidePanel, BorderLayout.WEST);

        // Create table for songs
        String[] columnNames = {"Title", "Artist", "Album", "Year", "Genre", "Comment"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5;  // Only the comment field is editable
            }
        };
        songTable = new JTable(tableModel);
        songTable.setRowHeight(30);

        JScrollPane scrollPane = new JScrollPane(songTable);
        add(scrollPane, BorderLayout.CENTER);

        // Enable drag and drop for the table
        new DropTarget(songTable, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {}

            @Override
            public void dragExit(DropTargetEvent dte) {}

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    Transferable transferable = dtde.getTransferable();
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        String selectedPlaylist = getSelectedPlaylist(); // Check selected playlist
                        if (selectedPlaylist != null) {
                            addSongsByDragAndDrop(files, false, selectedPlaylist);
                        } else {
                            addSongsByDragAndDrop(files, false, null);
                        }
                        dtde.dropComplete(true);
                    } else {
                        dtde.rejectDrop();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    dtde.rejectDrop();
                }
            }
        });

        // Enable drag from the song table
        songTable.setDragEnabled(true);
        songTable.setTransferHandler(new SongTransferHandler(database, this));

        // Create buttons and progress components
        JPanel buttonPanel = new JPanel();
        playButton = new JButton("Play");
        stopButton = new JButton("Stop");
        pauseButton = new JButton("Pause");
        unpauseButton = new JButton("Unpause");
        nextButton = new JButton("Next");
        previousButton = new JButton("Previous");
        shuffleButton = new JButton("Shuffle");
        repeatButton = new JButton("Repeat");

        // Add buttons to the panel
        buttonPanel.add(playButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(unpauseButton);
        buttonPanel.add(nextButton);
        buttonPanel.add(previousButton);
        buttonPanel.add(shuffleButton);
        buttonPanel.add(repeatButton);

        // Create and add the volume slider
        volumeSlider = new JSlider(0, 100, 50);  // Min 0, Max 100, Initial 50
        volumeSlider.addChangeListener(e -> adjustVolume(volumeSlider.getValue()));
        buttonPanel.add(new JLabel("Volume:"));
        buttonPanel.add(volumeSlider);

        // Create and add the timers and progress bar in the desired order
        elapsedTimeLabel = new JLabel("00:00");
        buttonPanel.add(elapsedTimeLabel);

        progressBar = new JProgressBar();
        buttonPanel.add(progressBar);

        remainingTimeLabel = new JLabel("00:00");
        buttonPanel.add(remainingTimeLabel);

        add(buttonPanel, BorderLayout.SOUTH);

        // File chooser
        fileChooser = new JFileChooser();

        // Initialize popup menus
        initializePopupMenus();

        // Add mouse listener to the scroll pane for the popup menu
        songTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (getSelectedPlaylist() != null) {
                        playlistPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                    } else {
                        libraryPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (getSelectedPlaylist() != null) {
                        playlistPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                    } else {
                        libraryPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        // Add key listener for Ctrl+L functionality
        songTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_L) {
                    highlightCurrentSong();
                }
            }
        });

        // Action Listeners for buttons
        playButton.addActionListener(e -> playSong());
        stopButton.addActionListener(e -> stopSong());
        pauseButton.addActionListener(e -> pauseSong());
        unpauseButton.addActionListener(e -> unpauseSong());
        nextButton.addActionListener(e -> nextSong());
        previousButton.addActionListener(e -> previousSong());
        shuffleButton.addActionListener(e -> toggleShuffle());
        repeatButton.addActionListener(e -> toggleRepeat());
    }

    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");

        JMenuItem openSongItem = new JMenuItem("Open a Song");
        openSongItem.addActionListener(e -> openFile());

        JMenuItem addSongItem = new JMenuItem("Add a Song");
        addSongItem.addActionListener(e -> addSong());

        JMenuItem deleteSongItem = new JMenuItem("Delete a Song");
        deleteSongItem.addActionListener(e -> deleteSong());

        JMenuItem createPlaylistItem = new JMenuItem("Create Playlist");
        createPlaylistItem.addActionListener(e -> createPlaylist());

        JMenuItem deletePlaylistItem = new JMenuItem("Delete Playlist");
        deletePlaylistItem.addActionListener(e -> deleteSelectedPlaylist());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0)); // Closes the application

        fileMenu.add(openSongItem);
        fileMenu.add(addSongItem);
        fileMenu.add(deleteSongItem);
        fileMenu.add(createPlaylistItem);
        fileMenu.add(deletePlaylistItem);
        fileMenu.addSeparator(); // Adds a separator line
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        // View menu
        JMenu viewMenu = new JMenu("View");
        JMenuItem columnSelector = new JMenuItem("Select Columns");
        columnSelector.addActionListener(e -> showColumnSelectionMenu(null));
        viewMenu.add(columnSelector);
        menuBar.add(viewMenu);

        // Controls menu
        JMenu controlsMenu = new JMenu("Controls");

        JMenuItem playItem = new JMenuItem("Play");
        playItem.addActionListener(e -> playSong());

        JMenuItem nextItem = new JMenuItem("Next");
        nextItem.addActionListener(e -> nextSong());

        JMenuItem previousItem = new JMenuItem("Previous");
        previousItem.addActionListener(e -> previousSong());

        controlsMenu.add(playItem);
        controlsMenu.add(nextItem);
        controlsMenu.add(previousItem);

        menuBar.add(controlsMenu);

        setJMenuBar(menuBar);
    }

    private void adjustVolume(int volume) {
        mp3Player.setVolume(volume);
    }

    private void initializePopupMenus() {
        // Popup menu for library view
        libraryPopupMenu = new JPopupMenu();
        JMenuItem openItem = new JMenuItem("Open Song");
        JMenuItem addItem = new JMenuItem("Add a Song");
        JMenuItem deleteItem = new JMenuItem("Delete a Song");
        JMenuItem addToPlaylistItem = new JMenuItem("Add to Playlist");

        openItem.addActionListener(e -> openSelectedSong()); // Add action for opening a song
        addItem.addActionListener(e -> addSong());
        deleteItem.addActionListener(e -> deleteSong());
        addToPlaylistItem.addActionListener(e -> {
            Point p = MouseInfo.getPointerInfo().getLocation();
            SwingUtilities.convertPointFromScreen(p, songTable);
            MouseEvent me = new MouseEvent(songTable, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                    0, p.x, p.y, 1, false);
            showPlaylistMenu(me);
        });

        libraryPopupMenu.add(openItem);  // Add open song option to the library popup menu
        libraryPopupMenu.add(addItem);
        libraryPopupMenu.add(deleteItem);
        libraryPopupMenu.add(addToPlaylistItem);

        // Popup menu for playlist view
        playlistPopupMenu = new JPopupMenu();
        JMenuItem addSongToPlaylistItem = new JMenuItem("Add a Song to Playlist");
        JMenuItem deleteSongFromPlaylistItem = new JMenuItem("Delete a Song from Playlist");

        addSongToPlaylistItem.addActionListener(e -> addSong());
        deleteSongFromPlaylistItem.addActionListener(e -> deleteSongFromPlaylist());

        playlistPopupMenu.add(addSongToPlaylistItem);
        playlistPopupMenu.add(deleteSongFromPlaylistItem);
    }

    private void openSelectedSong() {
        int selectedRow = songTable.getSelectedRow();
        if (selectedRow != -1) {
            Song selectedSong = songList.get(selectedRow);
            mp3Player.stop();  // Stop any currently playing song
            mp3Player.play(selectedSong.getFilePath());
        } else {
            JOptionPane.showMessageDialog(this, "Please select a song to open.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSongFromPlaylist() {
        int selectedRow = songTable.getSelectedRow();
        String selectedPlaylist = getSelectedPlaylist();
        if (selectedRow != -1 && selectedPlaylist != null) {
            Song song = currentPlaylist.get(selectedRow);
            database.removeSongFromPlaylist(song, selectedPlaylist);
            loadPlaylistSongs(selectedPlaylist); // Refresh the playlist table after deleting a song
        }
    }

    private void deleteSelectedPlaylist() {
        String selectedPlaylist = getSelectedPlaylist();
        if (selectedPlaylist != null) {
            int confirmation = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete the playlist '" + selectedPlaylist + "'?", "Confirm Deletion", JOptionPane.YES_NO_OPTION);
            if (confirmation == JOptionPane.YES_OPTION) {
                database.deletePlaylist(selectedPlaylist);
                loadPlaylistsFromDatabase(); // Refresh the playlist tree after deleting a playlist
                showLibrary(); // Switch to library view after deleting a playlist
            }
        }
    }

    private void createPlaylist() {
        String playlistName = JOptionPane.showInputDialog(this, "Enter playlist name:");
        if (playlistName != null && !playlistName.trim().isEmpty()) {
            database.createPlaylist(playlistName);
            loadPlaylistsFromDatabase(); // Refresh the playlist tree after creating a new playlist
            loadPlaylistSongs(playlistName); // Optional: Automatically load the new playlist's songs in the main window
        }
    }

    private void loadSelectedPlaylist() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) playlistTree.getLastSelectedPathComponent();
        if (selectedNode != null && !selectedNode.isRoot()) {
            String playlistName = selectedNode.toString();
            loadPlaylistSongs(playlistName);
        }
    }

    private void loadPlaylistSongs(String playlistName) {
        currentPlaylist = new ArrayList<>(database.getSongsFromPlaylist(playlistName));
        refreshTableColumns();
        tableModel.setRowCount(0); // Clear the table
        for (Song song : currentPlaylist) {
            addSongToTable(song);
        }
    }

    private void showLibrary() {
        currentPlaylist = new ArrayList<>(songList); // Set the current playlist to a copy of the library songs
        refreshTableColumns();
        tableModel.setRowCount(0); // Clear the table
        for (Song song : currentPlaylist) {
            addSongToTable(song);
        }
    }

    private void showPlaylists() {
        tableModel.setRowCount(0);
        tableModel.setColumnCount(1); // Show only playlist names
        tableModel.setColumnIdentifiers(new Object[]{"Playlists"});

        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) playlistTreeModel.getRoot();
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            tableModel.addRow(new Object[]{childNode.toString()});
        }
    }

    private void loadPlaylistsFromDatabase() {
        List<String> playlists = database.getAllPlaylists();
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) playlistTreeModel.getRoot();
        rootNode.removeAllChildren(); // Clear existing nodes

        for (String playlist : playlists) {
            DefaultMutableTreeNode playlistNode = new DefaultMutableTreeNode(playlist);
            playlistTreeModel.insertNodeInto(playlistNode, rootNode, rootNode.getChildCount());
        }

        playlistTreeModel.reload(); // Refresh the tree model to show changes
    }

    private void showPlaylistMenu(MouseEvent e) {
        JPopupMenu playlistMenu = new JPopupMenu();

        List<String> playlists = database.getAllPlaylists();
        if (playlists.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No playlists available.", "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        for (String playlist : playlists) {
            if (!"Recent".equalsIgnoreCase(playlist)) {
                JMenuItem playlistItem = new JMenuItem(playlist);
                playlistItem.addActionListener(ev -> addSelectedSongToPlaylist(playlist));
                playlistMenu.add(playlistItem);
            }
        }

        playlistMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void showPlaylistContextMenu(MouseEvent e) {
        JPopupMenu contextMenu = new JPopupMenu();

        JMenuItem openInNewWindow = new JMenuItem("Open in new window");
        openInNewWindow.addActionListener(ev -> openPlaylistInNewWindow());

        contextMenu.add(openInNewWindow);
        contextMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void openPlaylistInNewWindow() {
        String selectedPlaylist = getSelectedPlaylist();
        if (selectedPlaylist != null) {
            JFrame newWindow = new JFrame("Playlist - " + selectedPlaylist);
            newWindow.setSize(600, 400);
            newWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Only close this window

            // Create a table model and table for the new window
            DefaultTableModel newTableModel = new DefaultTableModel(new String[]{"Title", "Artist", "Album", "Year", "Genre", "Comment"}, 0);
            JTable newSongTable = new JTable(newTableModel);
            newSongTable.setRowHeight(30);
            JScrollPane newScrollPane = new JScrollPane(newSongTable);

            // Load songs for the selected playlist into the new window
            List<Song> songs = database.getSongsFromPlaylist(selectedPlaylist);
            for (Song song : songs) {
                newTableModel.addRow(new Object[]{song.getTitle(), song.getArtist(), song.getAlbum(), song.getYear(), song.getGenre(), song.getComment()});
            }

            // Create playback buttons for the new window
            JPanel buttonPanel = new JPanel();
            JButton playButton = new JButton("Play");
            JButton stopButton = new JButton("Stop");
            JButton pauseButton = new JButton("Pause");
            JButton unpauseButton = new JButton("Unpause");
            JButton nextButton = new JButton("Next");
            JButton previousButton = new JButton("Previous");

            // Set up the playback logic
            List<Song> currentPlaylist = songs; // Set the current playlist to the songs in this window
            MP3Player mp3Player = new MP3Player(); // Create a new MP3 player for this window
            int[] currentSongIndex = {-1}; // Use an array to hold the index so it can be modified within listeners

            playButton.addActionListener(e -> {
                int selectedRow = newSongTable.getSelectedRow();
                if (selectedRow != -1) {
                    mp3Player.stop();  // Stop any currently playing song
                    currentSongIndex[0] = selectedRow;
                    Song song = currentPlaylist.get(currentSongIndex[0]);
                    mp3Player.play(song.getFilePath());
                }
            });

            stopButton.addActionListener(e -> mp3Player.stop());

            pauseButton.addActionListener(e -> mp3Player.pause());

            unpauseButton.addActionListener(e -> mp3Player.unpause());

            nextButton.addActionListener(e -> {
                if (!currentPlaylist.isEmpty()) {
                    mp3Player.stop();  // Stop any currently playing song
                    currentSongIndex[0] = (currentSongIndex[0] + 1) % currentPlaylist.size();
                    newSongTable.setRowSelectionInterval(currentSongIndex[0], currentSongIndex[0]);
                    Song song = currentPlaylist.get(currentSongIndex[0]);
                    mp3Player.play(song.getFilePath());
                }
            });

            previousButton.addActionListener(e -> {
                if (!currentPlaylist.isEmpty()) {
                    mp3Player.stop();  // Stop any currently playing song
                    currentSongIndex[0] = (currentSongIndex[0] - 1 + currentPlaylist.size()) % currentPlaylist.size();
                    newSongTable.setRowSelectionInterval(currentSongIndex[0], currentSongIndex[0]);
                    Song song = currentPlaylist.get(currentSongIndex[0]);
                    mp3Player.play(song.getFilePath());
                }
            });

            // Add buttons to the button panel
            buttonPanel.add(playButton);
            buttonPanel.add(stopButton);
            buttonPanel.add(pauseButton);
            buttonPanel.add(unpauseButton);
            buttonPanel.add(nextButton);
            buttonPanel.add(previousButton);

            // Add a WindowListener to stop the song when the window is closed
            newWindow.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                    mp3Player.stop();
                }
            });

            // Add components to the new window
            newWindow.add(newScrollPane, BorderLayout.CENTER);
            newWindow.add(buttonPanel, BorderLayout.SOUTH);
            newWindow.setVisible(true);

            // Show library in the main window
            showLibrary();
        }
    }

    private void addSelectedSongToPlaylist(String playlistName) {
        int selectedRow = songTable.getSelectedRow();
        if (selectedRow != -1) {
            Song selectedSong = currentPlaylist.get(selectedRow);
            database.addSongToPlaylist(selectedSong, playlistName);
            JOptionPane.showMessageDialog(this, "Song added to playlist: " + playlistName);
        }
    }

    private void openFile() {
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (file.exists()) {
                mp3Player.play(file.getAbsolutePath());
            } else {
                JOptionPane.showMessageDialog(this, "File not found: " + file.getAbsolutePath());
            }
        }
    }

    private void addSong() {
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            Song newSong = Song.fromFile(file);

            // Check if the song already exists in the library
            boolean songExists = songList.stream()
                    .anyMatch(s -> s.getFilePath().equals(newSong.getFilePath()));

            if (!songExists) {
                songList.add(newSong);  // Add to the list if it doesn't exist
                database.addSong(newSong);  // Add to the database
                loadSongsToTable();  // Refresh the library view to show the new song
            } else {
                JOptionPane.showMessageDialog(this, "The song already exists in the library.", "Duplicate Song", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void deleteSong() {
        int selectedRow = songTable.getSelectedRow();
        if (selectedRow != -1) {
            Song song = songList.get(selectedRow);
            songList.remove(selectedRow);
            database.deleteSong(song);
            loadSongsToTable();
        }
    }

    private void playSong() {
        if (currentPlaylist.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No songs available to play.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int selectedRow = songTable.getSelectedRow();
        if (selectedRow != -1) {
            stopSong();  // Stop any currently playing song
            currentSongIndex = selectedRow;
            Song song = currentPlaylist.get(currentSongIndex);
            System.out.println("Playing song: " + song.getFilePath());

            // Automatically add to the "Recent" playlist
            database.setAddingToRecent(true); // Allow adding to Recent
            database.addSongToPlaylist(song, "Recent");
            database.setAddingToRecent(false); // Reset the flag

            // Initialize and start the song timer
            initializeAndStartTimer(song.getFilePath());

            mp3Player.play(song.getFilePath());
        } else {
            JOptionPane.showMessageDialog(this, "Please select a song to play.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void highlightCurrentSong() {
        if (currentSongIndex != -1) {
            songTable.setRowSelectionInterval(currentSongIndex, currentSongIndex);
        }
    }

    private void initializeAndStartTimer(String filePath) {
        // Get the duration of the song
        songDuration = mp3Player.getSongDuration(filePath);
        elapsedTime = 0;

        if (songTimer != null) {
            songTimer.stop();
        }

        // Set the progress bar maximum value to the song duration in seconds
        progressBar.setMaximum(songDuration);

        songTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                elapsedTime++;
                if (elapsedTime <= songDuration) {
                    // Update the progress bar value
                    progressBar.setValue(elapsedTime);
                    updateTimerUI();
                } else {
                    songTimer.stop();
                    if (isRepeatEnabled) {
                        playSong();
                    } else if (isShuffleEnabled) {
                        shufflePlay();
                    } else {
                        nextSong();
                    }
                }
            }
        });

        songTimer.start();
    }

    private void updateTimerUI() {
        progressBar.setValue(elapsedTime);
        elapsedTimeLabel.setText(formatTime(elapsedTime));
        remainingTimeLabel.setText(formatTime(songDuration - elapsedTime));
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    private void stopSong() {
        mp3Player.stop();
        if (songTimer != null) {
            songTimer.stop();
        }
        progressBar.setValue(0);
        elapsedTimeLabel.setText("00:00");
        remainingTimeLabel.setText("00:00");
    }

    private void pauseSong() {
        mp3Player.pause();
        if (songTimer != null) {
            songTimer.stop();
        }
    }

    private void unpauseSong() {
        mp3Player.unpause();
        if (songTimer != null) {
            songTimer.start();
        }
    }

    private void nextSong() {
        if (currentPlaylist.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No songs available to play.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        stopSong();  // Stop any currently playing song

        if (isShuffleEnabled) {
            shufflePlay(); // Play a random song if shuffle is enabled
        } else {
            currentSongIndex = (currentSongIndex + 1) % currentPlaylist.size(); // Move to the next song
            songTable.setRowSelectionInterval(currentSongIndex, currentSongIndex); // Highlight the next song in the UI
            playSong(); // Play the next song
        }
    }

    private void previousSong() {
        if (!currentPlaylist.isEmpty()) {
            stopSong();
            currentSongIndex = (currentSongIndex - 1 + currentPlaylist.size()) % currentPlaylist.size();
            songTable.setRowSelectionInterval(currentSongIndex, currentSongIndex);
            playSong();
        }
    }

    private void shufflePlay() {
        Random random = new Random();
        currentSongIndex = random.nextInt(currentPlaylist.size());
        songTable.setRowSelectionInterval(currentSongIndex, currentSongIndex);
        playSong();
    }

    private void toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled;
        shuffleButton.setText(isShuffleEnabled ? "Shuffle: ON" : "Shuffle: OFF");
    }

    private void toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled;
        repeatButton.setText(isRepeatEnabled ? "Repeat: ON" : "Repeat: OFF");
    }

    private void loadSongsToTable() {
        Set<Song> uniqueSongs = new LinkedHashSet<>(database.getAllSongs()); // LinkedHashSet maintains order and removes duplicates
        songList.clear(); // Clear the existing list
        songList.addAll(uniqueSongs); // Add all unique songs

        refreshTableColumns();
        for (Song song : songList) {
            addSongToTable(song);
        }
    }

    private void addSongToTable(Song song) {
        List<String> visibleColumns = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : columnVisibility.entrySet()) {
            if (entry.getValue()) {
                visibleColumns.add(entry.getKey());
            }
        }

        Object[] row = new Object[visibleColumns.size()];
        int colIndex = 0;
        for (String column : visibleColumns) {
            switch (column) {
                case "Title":
                    row[colIndex++] = song.getTitle();
                    break;
                case "Artist":
                    row[colIndex++] = song.getArtist();
                    break;
                case "Album":
                    row[colIndex++] = song.getAlbum();
                    break;
                case "Year":
                    row[colIndex++] = song.getYear();
                    break;
                case "Genre":
                    row[colIndex++] = song.getGenre();
                    break;
                case "Comment":
                    row[colIndex++] = song.getComment();
                    break;
            }
        }
        tableModel.addRow(row);
    }

    private void refreshTableColumns() {
        tableModel.setRowCount(0); // Clear the table
        tableModel.setColumnCount(0); // Clear all columns

        List<String> visibleColumns = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : columnVisibility.entrySet()) {
            if (entry.getValue()) {
                tableModel.addColumn(entry.getKey());
                visibleColumns.add(entry.getKey());
            }
        }

        if (!visibleColumns.isEmpty()) {
            for (Song song : currentPlaylist) {
                addSongToTable(song);
            }
        }
    }

    private void showColumnSelectionMenu(MouseEvent e) {
        JPopupMenu columnMenu = new JPopupMenu();

        for (String columnName : columnVisibility.keySet()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(columnName, columnVisibility.get(columnName));
            item.addActionListener(ev -> {
                columnVisibility.put(columnName, item.isSelected());
                refreshTableColumns();
                loadSongsToTable();
            });
            columnMenu.add(item);
        }

        if (e != null) {
            columnMenu.show(e.getComponent(), e.getX(), e.getY());
        } else {
            columnMenu.show(this, getWidth() / 2, getHeight() / 2);
        }
    }

    public void addSongsByDragAndDrop(List<File> files, boolean fromLibrary, String playlistName) {
        String currentView = getCurrentView();

        for (File file : files) {
            Song newSong = Song.fromFile(file);

            // Check if the song already exists in the library
            boolean songExistsInLibrary = songList.stream()
                    .anyMatch(s -> s.getFilePath().equals(newSong.getFilePath()));

            Song songToAdd;

            if (!songExistsInLibrary) {
                // Add to library if it doesn't exist
                songList.add(newSong);
                database.addSong(newSong);
                loadSongsToTable();  // Refresh the library view to show the new song
                songToAdd = newSong;
            } else {
                // The song already exists in the library, retrieve it
                songToAdd = songList.stream()
                        .filter(s -> s.getFilePath().equals(newSong.getFilePath()))
                        .findFirst()
                        .orElse(newSong);
            }

            // Allow adding the song to the playlist regardless of whether it's in the library
            if (playlistName != null && songToAdd != null) {
                database.addSongToPlaylist(songToAdd, playlistName);
                refreshPlaylistWindow(playlistName);  // Refresh the playlist window to show the new song
            }
        }

        restoreView(currentView);
    }

    private String getCurrentView() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) playlistTree.getLastSelectedPathComponent();
        if (selectedNode != null && !selectedNode.isRoot()) {
            return selectedNode.toString();
        }

        return "Library";
    }

    private void restoreView(String view) {
        if (view.equals("Library")) {
            showLibrary();
        } else {
            selectPlaylistInTree(view);
            loadPlaylistSongs(view);
        }
    }

    private void selectPlaylistInTree(String playlistName) {
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) playlistTreeModel.getRoot();
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            if (childNode.toString().equals(playlistName)) {
                TreePath path = new TreePath(childNode.getPath());
                playlistTree.setSelectionPath(path);
                break;
            }
        }
    }

    public JTable getSongTable() {
        return songTable;
    }

    public String getSelectedPlaylist() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) playlistTree.getLastSelectedPathComponent();
        if (selectedNode != null && !selectedNode.isRoot()) {
            return selectedNode.toString();
        }
        return null;
    }

    public void refreshPlaylistWindow(String playlistName) {
        Frame[] frames = JFrame.getFrames();
        for (Frame frame : frames) {
            if (frame instanceof JFrame) {
                JFrame jFrame = (JFrame) frame;
                if (jFrame.getTitle().equals("Playlist - " + playlistName)) {
                    DefaultTableModel newTableModel = (DefaultTableModel) ((JTable) ((JScrollPane) ((JPanel) jFrame.getContentPane()).getComponent(0)).getViewport().getView()).getModel();
                    newTableModel.setRowCount(0);
                    List<Song> songs = database.getSongsFromPlaylist(playlistName);
                    for (Song song : songs) {
                        newTableModel.addRow(new Object[]{song.getTitle(), song.getArtist(), song.getAlbum(), song.getYear(), song.getGenre(), song.getComment()});
                    }
                    break;
                }
            }
        }
    }

    public Song getSongFromRow(int rowIndex) {
        return songList.get(rowIndex);
    }

    public JTree getPlaylistTree() {
        return playlistTree;
    }

    public DefaultTableModel getTableModel() {
        return tableModel;
    }

    public List<Song> getSongList() {
        return songList;
    }
}

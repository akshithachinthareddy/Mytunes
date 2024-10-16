package mytunes;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private static final String URL = "jdbc:mysql://localhost:3306/mytunes";
    private static final String USER = "root";
    private static final String PASSWORD = ""; // Update with your MySQL password
    private boolean isAddingToRecent = false;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Database() {
        createTables();
    }

    public void createTables() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD); Statement stmt = conn.createStatement()) {
            String createSongsTable = "CREATE TABLE IF NOT EXISTS Songs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "title VARCHAR(255)," +
                    "artist VARCHAR(255)," +
                    "album VARCHAR(255)," +
                    "year VARCHAR(10)," + // Updated to VARCHAR(10) to handle longer year values
                    "genre VARCHAR(255)," +
                    "comment TEXT," +
                    "file_path VARCHAR(255) UNIQUE" + // Ensure no duplicate file paths
                    ")";
            stmt.execute(createSongsTable);

            String createPlaylistsTable = "CREATE TABLE IF NOT EXISTS Playlists (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(255) UNIQUE NOT NULL" +
                    ")";
            stmt.execute(createPlaylistsTable);

            String createPlaylistSongsTable = "CREATE TABLE IF NOT EXISTS PlaylistSongs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "playlist_id INT," +
                    "song_id INT," +
                    "FOREIGN KEY (playlist_id) REFERENCES Playlists(id) ON DELETE CASCADE," +
                    "FOREIGN KEY (song_id) REFERENCES Songs(id) ON DELETE CASCADE" +
                    ")";
            stmt.execute(createPlaylistSongsTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addSong(Song song) {
        if (!songExists(song)) {
            String sql = "INSERT INTO Songs(title, artist, album, year, genre, comment, file_path) VALUES(?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, song.getTitle());
                pstmt.setString(2, song.getArtist());
                pstmt.setString(3, song.getAlbum());
                pstmt.setString(4, song.getYear());
                pstmt.setString(5, song.getGenre());
                pstmt.setString(6, song.getComment());
                pstmt.setString(7, song.getFilePath());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean songExists(Song song) {
        String sql = "SELECT COUNT(*) FROM Songs WHERE file_path = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, song.getFilePath());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void deleteSong(Song song) {
        // Delete the song from the library and all playlists
        String sql = "DELETE FROM Songs WHERE file_path = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, song.getFilePath());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Song> getAllSongs() {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT * FROM Songs";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Song song = new Song(
                        rs.getInt("id"),  // Get the song ID
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getString("album"),
                        rs.getString("year"),
                        rs.getString("genre"),
                        rs.getString("comment"),
                        rs.getString("file_path")
                );
                songs.add(song);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return songs;
    }

    public void createPlaylist(String name) {
        if (!playlistExists(name)) {
            String sql = "INSERT INTO Playlists(name) VALUES(?)";
            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public List<String> getAllPlaylists() {
        List<String> playlists = new ArrayList<>();
        String sql = "SELECT name FROM Playlists";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                playlists.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return playlists;
    }

    public List<Song> getSongsFromPlaylist(String playlistName) {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT Songs.* FROM Songs " +
                "JOIN PlaylistSongs ON Songs.id = PlaylistSongs.song_id " +
                "JOIN Playlists ON Playlists.id = PlaylistSongs.playlist_id " +
                "WHERE Playlists.name = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playlistName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Song song = new Song(
                        rs.getInt("id"),  // Get the song ID
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getString("album"),
                        rs.getString("year"),
                        rs.getString("genre"),
                        rs.getString("comment"),
                        rs.getString("file_path")
                );
                songs.add(song);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return songs;
    }

    public void addSongToPlaylist(Song song, String playlistName) {
        // Prevent manually adding songs to "Recent" except during playback
        if ("Recent".equalsIgnoreCase(playlistName) && !isAddingToRecent) {
            System.out.println("Cannot manually add songs to the 'Recent' playlist.");
            return;
        }

        String checkSongInPlaylistSql = "SELECT COUNT(*) FROM PlaylistSongs " +
                "JOIN Playlists ON PlaylistSongs.playlist_id = Playlists.id " +
                "WHERE Playlists.name = ? AND PlaylistSongs.song_id = ?";
        String getPlaylistIdSql = "SELECT id FROM Playlists WHERE name = ?";
        String getSongIdSql = "SELECT id FROM Songs WHERE file_path = ?";
        String insertSql = "INSERT INTO PlaylistSongs(playlist_id, song_id) VALUES(?, ?)";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement checkStmt = conn.prepareStatement(checkSongInPlaylistSql);
             PreparedStatement getPlaylistIdStmt = conn.prepareStatement(getPlaylistIdSql);
             PreparedStatement getSongIdStmt = conn.prepareStatement(getSongIdSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            // Get playlist ID
            getPlaylistIdStmt.setString(1, playlistName);
            ResultSet playlistRs = getPlaylistIdStmt.executeQuery();
            if (playlistRs.next()) {
                int playlistId = playlistRs.getInt("id");

                // Get song ID
                getSongIdStmt.setString(1, song.getFilePath());
                ResultSet songRs = getSongIdStmt.executeQuery();
                if (songRs.next()) {
                    int songId = songRs.getInt("id");

                    // Check if the song is already in the playlist
                    checkStmt.setString(1, playlistName);
                    checkStmt.setInt(2, songId);
                    ResultSet checkRs = checkStmt.executeQuery();
                    if (checkRs.next() && checkRs.getInt(1) == 0) {
                        // If the song is not already in the playlist, add it
                        insertStmt.setInt(1, playlistId);
                        insertStmt.setInt(2, songId);
                        insertStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deletePlaylist(String playlistName) {
        String sql = "DELETE FROM Playlists WHERE name = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playlistName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeSongFromPlaylist(Song song, String playlistName) {
        String getPlaylistIdSql = "SELECT id FROM Playlists WHERE name = ?";
        String deleteSongFromPlaylistSql = "DELETE FROM PlaylistSongs WHERE playlist_id = ? AND song_id = ? LIMIT 1";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement getPlaylistIdStmt = conn.prepareStatement(getPlaylistIdSql);
             PreparedStatement deleteSongStmt = conn.prepareStatement(deleteSongFromPlaylistSql)) {

            // Get the playlist ID
            getPlaylistIdStmt.setString(1, playlistName);
            ResultSet playlistRs = getPlaylistIdStmt.executeQuery();
            if (playlistRs.next()) {
                int playlistId = playlistRs.getInt("id");

                // Delete only the specific instance of the song from the playlist
                deleteSongStmt.setInt(1, playlistId);
                deleteSongStmt.setInt(2, song.getId());
                deleteSongStmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean playlistExists(String playlistName) {
        String sql = "SELECT COUNT(*) FROM Playlists WHERE name = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playlistName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isAddingToRecent() {
        return isAddingToRecent;
    }

    public void setAddingToRecent(boolean addingToRecent) {
        isAddingToRecent = addingToRecent;
    }
}

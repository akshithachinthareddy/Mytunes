package mytunes;

import java.io.File;
import java.util.Objects;

public class Song {
    private int id;  // The song ID
    private String title;
    private String artist;
    private String album;
    private String year;
    private String genre;
    private String comment;
    private String filePath;

    public Song(int id, String title, String artist, String album, String year, String genre, String comment, String filePath) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.year = year;
        this.genre = genre;
        this.comment = comment;
        this.filePath = filePath;
    }

    // Method to create a Song object from a File
    public static Song fromFile(File file) {
        String title = file.getName(); // Use file name as the title
        String filePath = file.getAbsolutePath();
        
        // Simulate extracting year from file name or metadata (you can update this logic)
        String year = "2023";  // Example: use the current year as a placeholder
        if (year.length() > 4) {
            year = year.substring(0, 4); // Ensure the year is at most 4 characters
        }

        // Other fields can be initialized to default values or parsed from the file if available
        return new Song(0, title, "Unknown Artist", "Unknown Album", year, "Unknown Genre", "No Comments", filePath);
    }

    // Getters and setters for the fields
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        if (year.length() > 4) {
            this.year = year.substring(0, 4); // Ensure the year is at most 4 characters
        } else {
            this.year = year;
        }
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Song song = (Song) obj;
        return filePath.equals(song.filePath); // Compare based on file path
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath); // Use file path for hash code
    }
}

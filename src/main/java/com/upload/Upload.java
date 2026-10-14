package upload ;

public class Upload {
    private String nom;
    private String extension;
    private Byte[] bytes;

    public Upload(String nom, String extension, Byte[] bytes) {
        this.nom = nom;
        this.extension = extension;
        this.bytes = bytes;
    }

    public String getNom() {
        return nom;
    }

    

}
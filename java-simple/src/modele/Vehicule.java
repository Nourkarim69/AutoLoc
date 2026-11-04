package modele;

public class Vehicule {
    private String matricule;
    private String marque;
    private int annee;
    private boolean dispo;

    // Association "louer" : un véhicule est loué par 0..1 client
    private Client client;

    public Vehicule(String matricule, String marque, int annee) {
        this.matricule = matricule;
        this.marque = marque;
        this.annee = annee;
        this.dispo = true;
    }

    public void setDispo(boolean dispo) { this.dispo = dispo; }
    public boolean getDispo() { return dispo; }

    public String getMatricule() { return matricule; }
    public String getMarque() { return marque; }
    public int getAnnee() { return annee; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
}

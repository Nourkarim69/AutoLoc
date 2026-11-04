package modele;

import java.util.ArrayList;
import java.util.List;

public class Client {
    private int id;
    private String nom;

    // Association "louer" : un client loue 0..* véhicules
    private List<Vehicule> vehicules = new ArrayList<>();

    public Client(int id, String nom) {
        this.id = id;
        this.nom = nom;
    }

    public void louer(Vehicule v) {
        if (v.getDispo()) {
            vehicules.add(v);
            v.setClient(this);
            v.setDispo(false);
            System.out.println(nom + " loue le véhicule " + v.getMatricule());
        } else {
            System.out.println("Le véhicule " + v.getMatricule() + " n'est pas disponible.");
        }
    }

    public int getId() { return id; }
    public String getNom() { return nom; }
    public List<Vehicule> getVehicules() { return vehicules; }
}

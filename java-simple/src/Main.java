import modele.Client;
import modele.Vehicule;

public class  Main {
    public static void main(String[] args) {
        Client c = new Client(1, "Nour");
        Vehicule v1 = new Vehicule("123-TU-456", "Peugeot", 2020);
        Vehicule v2 = new Vehicule("789-TU-012", "Renault", 2022);

        c.louer(v1);
        c.louer(v2);
        c.louer(v1); // déjà loué -> pas disponible

        System.out.println(c.getNom() + " a loué " + c.getVehicules().size() + " véhicule(s).");
    }
}

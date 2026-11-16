package fr.ece.autoloc.perf;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.repository.ReservationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * Performance measurements used in the report (not part of the normal test suite:
 * the class name does not end with "Test", so "mvnw test" skips it).
 *
 * Runs on a separate, throw-away database (autoloc_perf), never on autoloc_db:
 *   mvnw test -Dtest=PerformanceBenchmark -Dsurefire.failIfNoSpecifiedTests=false
 * Optional: -Dperf.mode=nplus1 (only the N+1 part), -Dperf.out=results.json,
 *           -Dspring.jpa.properties.hibernate.default_batch_fetch_size=1 (turn batch fetching off).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/autoloc_perf?createDatabaseIfNotExist=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.profiles.active=perf",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=WARN",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
class PerformanceBenchmark {

    private static final int VEHICULES = 50;

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory emf;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired ReservationRepository reservations;
    @Autowired ObjectMapper json;

    private Statistics stats;
    private int reservationCount;

    @Test
    void mesurer() throws Exception {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        String mode = System.getProperty("perf.mode", "all");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batchFetchSize", emf.getProperties().get("hibernate.default_batch_fetch_size"));

        seed(1000);
        out.put("nplus1", nPlusOne(new int[]{50, 100, 250, 500, 1000}));
        if (!mode.equals("nplus1")) {
            out.put("pagination", pagination(new int[]{20, 100, 500, 1000}));
            out.put("index", index(new int[]{1_000, 10_000, 50_000, 100_000}));
            out.put("bcrypt", bcrypt(new int[]{8, 10, 12, 14}));
        }
        Path file = Path.of(System.getProperty("perf.out", "target/perf-results.json"));
        Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(out));
        System.out.println("PERF RESULTS -> " + file.toAbsolutePath());
    }

    // ---------------------------------------------------------------- data

    private void seed(int clients) {
        jdbc.update("insert into agence(nom, ville, adresse, telephone) values ('Paris', 'Paris', 'a', '0'), ('Lyon', 'Lyon', 'b', '0'), ('Marseille', 'Marseille', 'c', '0')");
        List<Long> agences = jdbc.queryForList("select id_agence from agence", Long.class);
        StringJoiner v = new StringJoiner(",");
        for (int i = 0; i < VEHICULES; i++) {
            v.add("('AA-%03d-ZZ', 'Peugeot', '208', 'CITADINE', 39.90, 'DISPONIBLE', %d)".formatted(i, agences.get(i % 3)));
        }
        jdbc.update("insert into vehicule(immatriculation, marque, modele, categorie, tarif_journalier, statut, id_agence) values " + v);
        jdbc.update("insert into equipement(libelle, tarif_journalier) values ('GPS', 5), ('Siege bebe', 4), ('Chaines neige', 6), ('Conducteur', 8)");
        StringJoiner c = new StringJoiner(",");
        for (int i = 0; i < clients; i++) {
            c.add("('Nom%d', 'Prenom%d', 'c%d@perf.fr', '0600000000', 'P%d', '2026-01-01', 'x')".formatted(i, i, i, i));
        }
        jdbc.update("insert into client(nom, prenom, email, telephone, num_permis, date_inscription, mot_de_passe) values " + c);
    }

    /** Adds reservations until the table holds {@code target} rows (client i, car i % 50, 2 options each). */
    private void fillReservations(int target) {
        List<Long> clients = jdbc.queryForList("select id_client from client order by id_client", Long.class);
        List<Long> cars = jdbc.queryForList("select id_vehicule from vehicule order by id_vehicule", Long.class);
        List<Long> options = jdbc.queryForList("select id_equipement from equipement order by id_equipement", Long.class);
        String[] statuts = {"EN_ATTENTE", "CONFIRMEE", "TERMINEE", "ANNULEE"};
        while (reservationCount < target) {
            int n = Math.min(2000, target - reservationCount);
            StringJoiner r = new StringJoiner(",");
            for (int k = 0; k < n; k++) {
                int i = reservationCount + k;
                int start = (i / VEHICULES) * 3;          // each car: back-to-back 3-day rentals
                r.add("(DATE_ADD('2024-01-01', INTERVAL %d DAY), DATE_ADD('2024-01-01', INTERVAL %d DAY), '%s', 120.00, 0, %d, %d)"
                        .formatted(start, start + 3, statuts[i % 4], clients.get(i % clients.size()), cars.get(i % VEHICULES)));
            }
            jdbc.update("insert into reservation(date_debut, date_fin, statut, montant_total, nb_prolongations, id_client, id_vehicule) values " + r);
            jdbc.update("insert into reservation_equipement(id_reservation, id_equipement) "
                    + "select id_reservation, ? from reservation where id_reservation > (select coalesce(max(id_reservation), 0) from reservation_equipement)", options.get(0));
            jdbc.update("insert into reservation_equipement(id_reservation, id_equipement) "
                    + "select id_reservation, ? from reservation r where not exists (select 1 from reservation_equipement e where e.id_reservation = r.id_reservation and e.id_equipement = ?)",
                    options.get(1), options.get(1));
            reservationCount += n;
        }
        jdbc.execute("analyze table reservation");
    }

    // ---------------------------------------------------------------- 1. N+1

    private List<Map<String, Object>> nPlusOne(int[] sizes) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int size : sizes) {
            fillReservations(size);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reservations", size);
            putMeasure(row, "naive", () -> em.createQuery("select r from Reservation r", Reservation.class).getResultList());
            putMeasure(row, "entityGraph", () -> reservations.findAll(PageRequest.of(0, size)).getContent());
            rows.add(row);
            System.out.println("N+1 " + row);
        }
        return rows;
    }

    /** Median time (ms) over 10 runs after 3 warm-up runs, and SQL statements of one run. */
    private void putMeasure(Map<String, Object> row, String name, Supplier<List<Reservation>> query) {
        for (int i = 0; i < 3; i++) run(query);
        double[] times = new double[10];
        long statements = 0;
        for (int i = 0; i < times.length; i++) {
            stats.clear();
            long t = System.nanoTime();
            int loaded = run(query);
            times[i] = (System.nanoTime() - t) / 1e6;
            statements = stats.getPrepareStatementCount();
            if (loaded != reservationCount) throw new IllegalStateException(name + " loaded " + loaded);
        }
        row.put(name + "Ms", median(times));
        row.put(name + "Queries", statements);
    }

    /** Runs the query in a fresh persistence context and touches every association, like the JSON serializer would. */
    private int run(Supplier<List<Reservation>> query) {
        return tx.execute(s -> {
            em.clear();
            List<Reservation> list = query.get();
            for (Reservation r : list) {
                r.getClient().getNom();
                r.getVehicule().getAgence();
                r.getEquipements().size();
            }
            return list.size();
        });
    }

    // ---------------------------------------------------------------- 2. pagination

    private List<Map<String, Object>> pagination(int[] pageSizes) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int pageSize : pageSizes) {
            double[] times = new double[10];
            int bytes = 0;
            for (int i = -3; i < times.length; i++) {
                long t = System.nanoTime();
                byte[] body = tx.execute(s -> {
                    em.clear();
                    return json.writeValueAsBytes(reservations.findAll(PageRequest.of(0, pageSize)).getContent());
                });
                if (i >= 0) times[i] = (System.nanoTime() - t) / 1e6;
                bytes = body.length;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pageSize", pageSize);
            row.put("ms", median(times));
            row.put("jsonBytes", bytes);
            rows.add(row);
            System.out.println("PAGE " + row);
        }
        return rows;
    }

    // ---------------------------------------------------------------- 3. index

    private List<Map<String, Object>> index(int[] sizes) {
        List<String> secondary = jdbc.queryForList(
                "select distinct index_name from information_schema.statistics where table_schema = database() "
                        + "and table_name = 'reservation' and index_name <> 'PRIMARY'", String.class);
        String others = secondary.stream().filter(n -> !n.equals("idx_reservation_dispo")).reduce((a, b) -> a + ", " + b).orElse("");
        String base = "select count(*) > 0 from reservation %s where id_vehicule = ? and statut in ('EN_ATTENTE', 'CONFIRMEE') "
                + "and id_reservation <> -1 and date_debut < ? and date_fin > ?";
        Map<String, String> variants = new LinkedHashMap<>();
        variants.put("composite", base.formatted(""));
        variants.put("fkOnly", base.formatted("ignore index (idx_reservation_dispo)"));
        variants.put("none", base.formatted("ignore index (" + String.join(", ", secondary) + ")"));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int size : sizes) {
            fillReservations(size);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reservations", size);
            for (var e : variants.entrySet()) {
                String sql = e.getValue();
                Object[] args = {1L, "2025-06-10", "2025-06-01"};
                for (int i = 0; i < 20; i++) jdbc.queryForObject(sql, Boolean.class, args);
                int runs = 200;
                double[] times = new double[runs];
                for (int i = 0; i < runs; i++) {
                    long t = System.nanoTime();
                    jdbc.queryForObject(sql, Boolean.class, (long) (i % VEHICULES) + 1, "2025-06-10", "2025-06-01");
                    times[i] = (System.nanoTime() - t) / 1e6;
                }
                Map<String, Object> plan = jdbc.queryForMap("explain " + sql, args);
                row.put(e.getKey() + "Ms", median(times));
                row.put(e.getKey() + "Rows", plan.get("rows"));
                row.put(e.getKey() + "Key", plan.get("key"));
            }
            rows.add(row);
            System.out.println("INDEX " + row);
        }
        row0Info(rows, others);
        return rows;
    }

    private void row0Info(List<Map<String, Object>> rows, String others) {
        if (!rows.isEmpty()) rows.get(0).put("otherIndexes", others);
    }

    // ---------------------------------------------------------------- 4. BCrypt

    private List<Map<String, Object>> bcrypt(int[] costs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int cost : costs) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(cost);
            encoder.encode("warmup123");
            double[] times = new double[5];
            for (int i = 0; i < times.length; i++) {
                long t = System.nanoTime();
                encoder.encode("MotDePasse123");
                times[i] = (System.nanoTime() - t) / 1e6;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("cost", cost);
            row.put("ms", median(times));
            rows.add(row);
            System.out.println("BCRYPT " + row);
        }
        return rows;
    }

    private static double median(double[] values) {
        double[] v = values.clone();
        Arrays.sort(v);
        double m = v.length % 2 == 1 ? v[v.length / 2] : (v[v.length / 2 - 1] + v[v.length / 2]) / 2;
        return Math.round(m * 100) / 100.0;
    }
}

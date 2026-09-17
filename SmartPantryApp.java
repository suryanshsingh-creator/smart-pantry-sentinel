import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

// Entity class modeling a perishable grocery item
class PantryItem implements Comparable<PantryItem> {
    private final String id;
    private final String name;
    private final String category;
    private final double quantity;
    private final String unit;
    private final LocalDate expiryDate;

    public PantryItem(String id, String name, String category, double quantity, String unit, LocalDate expiryDate) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.unit = unit;
        this.expiryDate = expiryDate;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public LocalDate getExpiryDate() { return expiryDate; }

    public long getDaysUntilExpiry() {
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    public String getStatusTag() {
        long days = getDaysUntilExpiry();
        if (days < 0) return "[EXPIRED]";
        if (days <= 2) return "[CRITICAL]";
        if (days <= 5) return "[EXPIRING SOON]";
        return "[FRESH]";
    }

    // Sort order: Earliest expiry date first (Min-Heap priority)
    @Override
    public int compareTo(PantryItem other) {
        int dateCmp = this.expiryDate.compareTo(other.expiryDate);
        if (dateCmp != 0) return dateCmp;
        return this.name.compareToIgnoreCase(other.name);
    }

    public String toCsv() {
        return String.join(",", id, name, category, String.valueOf(quantity), unit, expiryDate.toString());
    }

    public static PantryItem fromCsv(String line) {
        String[] tokens = line.split(",");
        if (tokens.length < 6) return null;
        try {
            return new PantryItem(
                tokens[0].trim(),
                tokens[1].trim(),
                tokens[2].trim(),
                Double.parseDouble(tokens[3].trim()),
                tokens[4].trim(),
                LocalDate.parse(tokens[5].trim())
            );
        } catch (Exception e) {
            return null;
        }
    }
}

// Business logic and persistence layer
class PantryManager {
    private final Map<String, PantryItem> itemMap = new ConcurrentHashMap<>();
    private final String storageFile = "pantry_inventory.csv";

    public PantryManager() {
        loadData();
    }

    public synchronized void addItem(String name, String category, double quantity, String unit, LocalDate expiry) {
        String id = "ITEM-" + (System.currentTimeMillis() % 10000);
        PantryItem item = new PantryItem(id, name, category, quantity, unit, expiry);
        itemMap.put(id, item);
        saveData();
    }

    public synchronized boolean removeItem(String id) {
        if (itemMap.remove(id) != null) {
            saveData();
            return true;
        }
        return false;
    }

    // Min-Heap retrieval: elements ordered by impending expiry
    public PriorityQueue<PantryItem> getExpiryPriorityQueue() {
        return new PriorityQueue<>(itemMap.values());
    }

    public List<PantryItem> getUrgentItems(int withinDays) {
        List<PantryItem> urgent = new ArrayList<>();
        for (PantryItem item : itemMap.values()) {
            if (item.getDaysUntilExpiry() <= withinDays) {
                urgent.add(item);
            }
        }
        urgent.sort(Comparator.comparing(PantryItem::getExpiryDate));
        return urgent;
    }

    public Map<String, Integer> getCategoryDistribution() {
        Map<String, Integer> counts = new HashMap<>();
        for (PantryItem item : itemMap.values()) {
            counts.put(item.getCategory(), counts.getOrDefault(item.getCategory(), 0) + 1);
        }
        return counts;
    }

    private synchronized void saveData() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(storageFile))) {
            for (PantryItem item : itemMap.values()) {
                writer.println(item.toCsv());
            }
        } catch (IOException e) {
            System.err.println("Could not save to file: " + e.getMessage());
        }
    }

    private void loadData() {
        File f = new File(storageFile);
        if (!f.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = reader.readLine()) != null) {
                PantryItem item = PantryItem.fromCsv(line);
                if (item != null) {
                    itemMap.put(item.getId(), item);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed reading stored data: " + e.getMessage());
        }
    }
}

// Main CLI Application with Background Sentinel Thread
public class SmartPantryApp {
    private static final Scanner scanner = new Scanner(System.in);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void main(String[] args) {
        PantryManager manager = new PantryManager();

        // Background monitor checking for critical/expired items periodically
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true); // Daemon thread exits when main program closes
            return t;
        });

        // Run background check every 60 seconds
        monitor.scheduleAtFixedRate(() -> runSilentCheck(manager), 10, 60, TimeUnit.SECONDS);

        boolean active = true;
        while (active) {
            displayMenu();
            int choice = promptInt("Choose an option (1-6): ");
            switch (choice) {
                case 1 -> handleAddItem(manager);
                case 2 -> displayInventoryByUrgency(manager);
                case 3 -> handleWasteAlerts(manager);
                case 4 -> handleConsumeItem(manager);
                case 5 -> displayPantryAnalytics(manager);
                case 6 -> {
                    System.out.println("Saving state. Exiting Smart Pantry...");
                    monitor.shutdown();
                    active = false;
                }
                default -> System.out.println("Invalid selection. Try again.");
            }
        }
    }

    private static void displayMenu() {
        System.out.println("\n==================================================");
        System.out.println("     SMART PANTRY & EXPIRY ALERT SYSTEM          ");
        System.out.println("==================================================");
        System.out.println("1. Add Grocery / Medication Item");
        System.out.println("2. View Inventory (Min-Heap Expiry Priority)");
        System.out.println("3. Run Recipe / Cook-Soon Waste Alert");
        System.out.println("4. Mark Item as Consumed / Discarded");
        System.out.println("5. View Pantry Category Analytics");
        System.out.println("6. Exit");
        System.out.println("--------------------------------------------------");
    }

    private static void handleAddItem(PantryManager manager) {
        System.out.println("\n-- Record New Item --");
        System.out.print("Item name (e.g., Whole Milk, Cheddar, Bread): ");
        String name = scanner.nextLine().trim();

        System.out.print("Category (Dairy, Produce, Bakery, Meat, Medicine): ");
        String category = scanner.nextLine().trim();

        double qty = promptDouble("Quantity: ");
        System.out.print("Unit (pcs, liters, kg, packs): ");
        String unit = scanner.nextLine().trim();

        LocalDate expiry = promptDate();
        manager.addItem(name, category, qty, unit, expiry);
        System.out.println("✓ Item registered in pantry.");
    }

    private static void displayInventoryByUrgency(PantryManager manager) {
        PriorityQueue<PantryItem> pq = manager.getExpiryPriorityQueue();
        if (pq.isEmpty()) {
            System.out.println("\nPantry is empty! No items recorded.");
            return;
        }

        System.out.println("\n" + "=".repeat(85));
        System.out.printf("%-10s | %-16s | %-12s | %-10s | %-12s | %-10s%n",
                "Item ID", "Name", "Category", "Quantity", "Expiry Date", "Urgency");
        System.out.println("=".repeat(85));

        // Poll from priority queue to print items ordered by earliest expiration
        while (!pq.isEmpty()) {
            PantryItem item = pq.poll();
            String qtyLabel = item.getQuantity() + " " + item.getUnit();
            System.out.printf("%-10s | %-16s | %-12s | %-10s | %-12s | %-10s%n",
                    item.getId(), item.getName(), item.getCategory(), qtyLabel,
                    item.getExpiryDate(), item.getStatusTag());
        }
        System.out.println("=".repeat(85));
    }

    private static void handleWasteAlerts(PantryManager manager) {
        List<PantryItem> urgent = manager.getUrgentItems(3);
        System.out.println("\n-- SMART RESCUE ALERTS (Items Expiring within 72 Hours) --");
        if (urgent.isEmpty()) {
            System.out.println("Good job! No items are at immediate risk of spoiling.");
            return;
        }

        System.out.println("Items requiring immediate attention:");
        for (PantryItem item : urgent) {
            long days = item.getDaysUntilExpiry();
            String timeText = days < 0 ? Math.abs(days) + " days ago!" : (days == 0 ? "TODAY" : "in " + days + " days");
            System.out.printf(" - %s (%s) expires %s!%n", item.getName(), item.getCategory(), timeText);
        }

        // Context-aware recipe suggestions based on matching categories
        System.out.println("\nRescue Suggestion:");
        boolean hasDairy = urgent.stream().anyMatch(i -> i.getCategory().equalsIgnoreCase("Dairy"));
        boolean hasBakery = urgent.stream().anyMatch(i -> i.getCategory().equalsIgnoreCase("Bakery"));
        boolean hasProduce = urgent.stream().anyMatch(i -> i.getCategory().equalsIgnoreCase("Produce"));

        if (hasDairy && hasBakery) {
            System.out.println("-> Suggestion: Pair your bakery and dairy items (e.g., grilled cheese, french toast).");
        } else if (hasProduce) {
            System.out.println("-> Suggestion: Use aging produce immediately for a vegetable stir-fry or smoothie.");
        } else {
            System.out.println("-> Suggestion: Freeze or batch-cook these items before the expiry window closes.");
        }
    }

    private static void handleConsumeItem(PantryManager manager) {
        System.out.print("\nEnter Item ID to remove: ");
        String id = scanner.nextLine().trim();
        if (manager.removeItem(id)) {
            System.out.println("✓ Item removed from pantry inventory.");
        } else {
            System.out.println("✗ Error: Item ID not found.");
        }
    }

    private static void displayPantryAnalytics(PantryManager manager) {
        Map<String, Integer> dist = manager.getCategoryDistribution();
        System.out.println("\n-- Category Breakdown --");
        if (dist.isEmpty()) {
            System.out.println("No items to analyze.");
            return;
        }
        dist.forEach((cat, count) -> System.out.printf(" • %-12s: %d item(s)%n", cat, count));
    }

    private static void runSilentCheck(PantryManager manager) {
        List<PantryItem> urgent = manager.getUrgentItems(1);
        if (!urgent.isEmpty()) {
            System.out.print("\n\n[SYSTEM NOTIFICATION]: " + urgent.size() + " item(s) are expiring within 24 hours!\nAction: ");
        }
    }

    private static int promptInt(String msg) {
        while (true) {
            System.out.print(msg);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid integer.");
            }
        }
    }

    private static double promptDouble(String msg) {
        while (true) {
            System.out.print(msg);
            try {
                double v = Double.parseDouble(scanner.nextLine().trim());
                if (v <= 0) {
                    System.out.println("Quantity must be positive.");
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid decimal or number.");
            }
        }
    }

    private static LocalDate promptDate() {
        while (true) {
            System.out.print("Expiry date (YYYY-MM-DD): ");
            String str = scanner.nextLine().trim();
            try {
                return LocalDate.parse(str, DATE_FORMAT);
            } catch (DateTimeParseException e) {
                System.out.println("Invalid format. Use strict YYYY-MM-DD.");
            }
        }
    }
}
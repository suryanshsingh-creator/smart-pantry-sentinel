# smart-pantry-sentinel
# Smart Pantry & Expiry Alert System

A lightweight, CLI-based automated inventory tracker and perishable alert system built in pure Java. The system optimizes grocery shelf-life tracking using Min-Heap priority scheduling, concurrent file-backed persistence, and a background sentinel daemon checking for imminent spoilage.

---

## Key Features

- **Min-Heap Priority Queue:** Automatically orders inventory by nearest expiration date for $O(\log n)$ insertion and $O(1)$ critical retrieval.
- **Concurrent Persistence:** Uses a thread-safe `ConcurrentHashMap` synchronized with a flat-file CSV database (`pantry_inventory.csv`).
- **Sentinel Daemon:** A dedicated background thread (`ScheduledExecutorService`) checks for expiring items every 60 seconds without blocking CLI interactions.
- **Smart Waste Rescue Engine:** Dynamically generates pairing ideas (e.g., dairy + bakery combos) to minimize domestic food waste.

---

## Prerequisites

- **Java Development Kit (JDK):** Version 17 or higher
- **Build Tools:** None required (compiles cleanly with native `javac`)

---

## Build & Execution Instructions

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/](https://github.com/)<your-username>/smart-pantry-sentinel.git
   cd smart-pantry-sentinel

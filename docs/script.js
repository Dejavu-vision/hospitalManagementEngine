const fs = require("fs");
const csv = require("csv-parser");

const INPUT_CSV = "indian_medicine_data.csv"; // Path to your real CSV
const OUTPUT_SQL = "medicines_5000_real.sql";

let count = 0;
let sql = "";

// Validate field
function isValid(v) {
  return v && v.trim() !== "" && v.toLowerCase() !== "unknown" && v.toLowerCase() !== "na";
}

// Normalize dosage form safely
function normalizeForm(form) {
  if (!form) return "Other";
  form = form.toLowerCase();
  if (form.includes("tablet")) return "Tablet";
  if (form.includes("capsule")) return "Capsule";
  if (form.includes("syrup") || form.includes("oral suspension")) return "Syrup";
  if (form.includes("injection") || form.includes("injectable")) return "Injection";
  if (form.includes("inhaler")) return "Inhaler";
  if (form.includes("gel")) return "Gel";
  return "Other";
}

// Normalize strength safely
function normalizeStrength(str) {
  if (!str) return "N/A";
  str = str.toLowerCase().replace(/\s+/g, "");
  if (str.includes("mg") || str.includes("mcg") || str.includes("g") || str.includes("ml")) return str.toUpperCase();
  return str;
}

// Map categories based on generic keywords
function getCategory(generic) {
  if (!generic) return "General";
  generic = generic.toLowerCase();
  if (generic.includes("amoxicillin") || generic.includes("cipro") || generic.includes("azithro") || generic.includes("metronidazole")) return "Antibiotic";
  if (generic.includes("paracetamol") || generic.includes("ibuprofen") || generic.includes("diclofenac")) return "Analgesic";
  if (generic.includes("atorvastatin") || generic.includes("aspirin") || generic.includes("clopidogrel") || generic.includes("telmisartan")) return "Cardiac";
  if (generic.includes("metformin") || generic.includes("glimepiride") || generic.includes("sitagliptin")) return "Antidiabetic";
  if (generic.includes("pantoprazole") || generic.includes("omeprazole") || generic.includes("ranitidine")) return "Antacid";
  if (generic.includes("cetirizine") || generic.includes("fexofenadine") || generic.includes("levocetirizine") || generic.includes("diphenhydramine")) return "Antihistamine";
  if (generic.includes("salbutamol") || generic.includes("budesonide") || generic.includes("montelukast") || generic.includes("formoterol")) return "Respiratory";
  return "General";
}

// Read CSV and generate SQL inserts
fs.createReadStream(INPUT_CSV)
  .pipe(csv())
  .on("data", (row) => {
    if (count >= 5000) return;

    const brand = row.name;
    const generic = row.short_composition1;
    const form = row.dosage_form || row.pack_form || "Other";
    const strength = row.pack_size_label || "N/A";

    if (!isValid(brand) || !isValid(generic) || !isValid(strength)) return;

    const safeBrand = brand.replace(/'/g, "''");
    const safeGeneric = generic.replace(/'/g, "''");
    const safeForm = normalizeForm(form);
    const safeStrength = normalizeStrength(strength);
    const category = getCategory(generic);

    sql += `INSERT INTO medicines (brand, category, form, generic_name, is_active, name, strength)
VALUES ('${safeBrand}', '${category}', '${safeForm}', '${safeGeneric}', b'1', '${safeBrand}', '${safeStrength}');\n`;

    count++;
  })
  .on("end", () => {
    fs.writeFileSync(OUTPUT_SQL, sql);
    console.log(`✅ 5000 real medicines SQL inserts generated → ${OUTPUT_SQL}`);
  })
  .on("error", (err) => {
    console.error("❌ Error reading CSV:", err);
  });
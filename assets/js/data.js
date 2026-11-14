/* =====================================================================
   Gem Shop prototype — ALL dummy data (BRD §06: dummy data only).
   SEED_RAW holds the hand-written records; buildSeed() (called from app.js
   on first load / reset) expands them into the full demo state: ~40 sales over
   the last 30 days, commissions, closings, expenses, approvals, log, alerts.
   ===================================================================== */
(function () {
  'use strict';

  const SEED_RAW = {
    shop: {
      name: 'Ceylon Gem House',
      tagline: 'Fine Ceylon Gems · Since 1998',
      address: '42 Main Street, Ella 90090, Badulla District, Sri Lanka',
      phone: '+94 57 222 8800',
      email: 'sales@ceylongemhouse.lk',
      website: 'www.ceylongemhouse.lk',
      vatNo: '114567890-7000',
      vatRate: 18,
      vatInclusive: true,       // assumption confirmed in plan: agreed prices include VAT
      ngjaNo: 'NGJA/DL/2024/0457',
      ngjaExpiryDays: 45,       // expiry is set relative to today so the reminder shows
      ngjaRemindDays: 60,
      printLicenceOnInvoice: true,
      largeExpenseLimit: 50000, // M6
      invoicePrefix: 'INV-',
      returnPolicyDays: 14
    },
    branches: [
      { id: 'ella', name: 'Ella', address: '42 Main Street, Ella', phone: '+94 57 222 8800', status: 'active', opened: '2019-03-01', manager: 'u1' },
      { id: 'b2', name: 'Branch 2 (opening January 2027)', short: 'Branch 2', address: 'To be confirmed', phone: '—', status: 'opening', opened: '2027-01-15', manager: 'u1' }
    ],
    users: [
      { id: 'u1', name: 'Ruwan Perera', role: 'owner', branch: 'all', email: 'owner@ceylongemhouse.lk', phone: '+94 77 123 4567', active: true, lastLogin: 0 },
      { id: 'u2', name: 'Nimal Silva', role: 'salesperson', branch: 'ella', email: 'nimal@ceylongemhouse.lk', phone: '+94 71 555 2101', active: true, lastLogin: 0 },
      { id: 'u3', name: 'Kasun Bandara', role: 'staff', branch: 'ella', email: 'kasun@ceylongemhouse.lk', phone: '+94 76 444 9830', active: true, lastLogin: 1 },
      { id: 'u4', name: 'Dilani Fernando', role: 'accountant', branch: 'all', email: 'dilani@fernandoassociates.lk', phone: '+94 77 909 1188', active: true, lastLogin: 2 }
    ],
    lists: {
      gemTypes: ['Blue Sapphire', 'Yellow Sapphire', 'Pink Sapphire', 'Padparadscha', 'Star Sapphire', 'Ruby', "Cat's Eye", 'Alexandrite', 'Moonstone', 'Spinel', 'Garnet', 'Topaz', 'Other'],
      productTypes: ['Loose gem', 'Ring', 'Pendant', 'Earrings', 'Bracelet', 'Necklace', 'Other'],
      colours: ['Royal Blue', 'Cornflower Blue', 'Ceylon Blue', 'Grey Blue', 'Golden Yellow', 'Canary Yellow', 'Lemon', 'Hot Pink', 'Light Pink', 'Pinkish Orange', 'Sunset Pink', 'Pigeon Red', 'Deep Red', 'Wine Red', 'Honey', 'Green-Red', 'Green-Purple', 'Blue Sheen', 'Rainbow', 'Lavender', 'Sky Blue', 'London Blue', 'Red'],
      shapes: ['Oval', 'Cushion', 'Round', 'Pear', 'Emerald', 'Cabochon', 'Heart', 'Marquise', 'Princess'],
      treatments: ['Natural', 'Heated', 'Untreated'],
      labs: ['NGJA Gem Testing Laboratory', 'GIA', 'GRS (GemResearch Swisslab)', 'SSEF', 'Gübelin', 'Lotus Gemology'],
      expenseCategories: ['Rent', 'Electricity', 'Water', 'Phone & internet', 'Staff salaries & allowances', 'Transport', 'Packaging & gift boxes', 'Lab & certificate fees', 'Marketing & messages', 'Repairs', 'Other']
    },
    commissionRules: {
      defaultGuidePct: 10,
      slabs: [
        { upTo: 50000, amount: 500 },
        { upTo: 200000, amount: 1500 },
        { upTo: 500000, amount: 3000 },
        { upTo: null, amount: 5000 }
      ]
    },
    rates: { USD: 300, EUR: 325 },
    suppliers: [
      { id: 'SUP-01', name: 'Ratna Gems & Lapidary', contact: 'M. H. Faizal', phone: '+94 45 222 3141', address: '18 Main Street, Ratnapura', idType: 'BR', idNo: 'PV 00213456', notes: 'Sapphires and padparadscha, NGJA certified' },
      { id: 'SUP-02', name: 'Beruwala Gem Traders', contact: 'A. R. M. Rizwan', phone: '+94 34 227 6620', address: '7 China Fort, Beruwala', idType: 'BR', idNo: 'PV 00109822', notes: 'Rings, pendants and earrings' },
      { id: 'SUP-03', name: 'Elahera Mining Co-op', contact: 'K. Wijeratne', phone: '+94 66 225 9014', address: 'Elahera Road, Matale District', idType: 'BR', idNo: 'CS 2231', notes: 'Rough-to-cut loose gems, spinel, garnet' },
      { id: 'SUP-04', name: 'Sunil Jayasena (individual)', contact: 'Sunil Jayasena', phone: '+94 77 341 8890', address: '112 Pelmadulla Road, Ratnapura', idType: 'NIC', idNo: '197812345678', notes: 'Moonstone and cat\'s eye' },
      { id: 'SUP-05', name: 'Kandy Fine Jewellers', contact: 'Priyanka Senanayake', phone: '+94 81 223 5566', address: '24 Dalada Veediya, Kandy', idType: 'BR', idNo: 'PV 00451230', notes: 'Finished jewellery with Sri Lankan stones' }
    ],
    guides: [
      { id: 'G-01', name: 'Sunil Kumara', vehicle: 'Tuk-tuk', vehicleNo: 'UVA QA-4521', phone: '+94 77 812 3344', pct: 10, since: '2021-06-01' },
      { id: 'G-02', name: 'Chaminda Rathnayake', vehicle: 'Van', vehicleNo: 'WP PH-7781', phone: '+94 71 662 0019', pct: 12, since: '2020-02-14' },
      { id: 'G-03', name: 'Pradeep Herath', vehicle: 'Taxi', vehicleNo: 'CP CAB-3307', phone: '+94 76 301 4422', pct: 8, since: '2022-09-10' },
      { id: 'G-04', name: 'Lahiru Dissanayake', vehicle: 'Tuk-tuk', vehicleNo: 'UVA QB-1190', phone: '+94 75 900 6612', pct: 10, since: '2023-03-03' },
      { id: 'G-05', name: 'Ajith Gunawardena', vehicle: 'Van', vehicleNo: 'SP PD-4410', phone: '+94 77 456 7788', pct: 12, since: '2019-11-20' },
      { id: 'G-06', name: 'Roshan Weerasinghe', vehicle: 'Taxi', vehicleNo: 'UVA CAB-0912', phone: '+94 70 225 1903', pct: 5, since: '2024-07-01' }
    ],
    customers: [
      ['Anna Müller', 'Germany', 'anna.mueller@web.de', '+49 151 2345 6789', true],
      ['Lukas Schneider', 'Germany', 'lukas.schneider@gmx.de', '+49 160 9876 5432', true],
      ['Sophie Wagner', 'Germany', 'sophie.w@t-online.de', '+49 171 111 2233', false],
      ['Felix Becker', 'Germany', 'felix.becker@mail.de', '+49 152 4455 6677', true],
      ['Oliver Smith', 'United Kingdom', 'oliver.smith@gmail.com', '+44 7700 900123', true],
      ['Charlotte Brown', 'United Kingdom', 'charlotte.b@outlook.com', '+44 7700 900456', true],
      ['James Wilson', 'United Kingdom', 'jwilson@btinternet.com', '+44 7700 900789', false],
      ['Emily Taylor', 'United Kingdom', 'emily.taylor@yahoo.co.uk', '+44 7700 900321', true],
      ['Camille Dubois', 'France', 'camille.dubois@orange.fr', '+33 6 12 34 56 78', true],
      ['Louis Martin', 'France', 'louis.martin@free.fr', '+33 6 98 76 54 32', false],
      ['Chloé Bernard', 'France', 'chloe.bernard@laposte.net', '+33 7 11 22 33 44', true],
      ['Michael Johnson', 'USA', 'mjohnson@gmail.com', '+1 415 555 0142', true],
      ['Jessica Miller', 'USA', 'jess.miller@icloud.com', '+1 212 555 0199', true],
      ['David Garcia', 'USA', 'dgarcia@hotmail.com', '+1 305 555 0110', false],
      ['Ashley Davis', 'USA', 'ashley.davis@gmail.com', '+1 512 555 0177', true],
      ['Jack Thompson', 'Australia', 'jack.t@bigpond.com', '+61 412 345 678', true],
      ['Olivia White', 'Australia', 'olivia.white@gmail.com', '+61 423 456 789', true],
      ['Noah Harris', 'Australia', 'noah.harris@outlook.com', '+61 434 567 890', false],
      ['Daan de Vries', 'Netherlands', 'daan.devries@ziggo.nl', '+31 6 1234 5678', true],
      ['Emma Jansen', 'Netherlands', 'emma.jansen@kpnmail.nl', '+31 6 8765 4321', true],
      ['Sanne Bakker', 'Netherlands', 'sanne.bakker@gmail.com', '+31 6 2468 1357', false],
      ['Priya Sharma', 'India', 'priya.sharma@gmail.com', '+91 98200 12345', true],
      ['Arjun Mehta', 'India', 'arjun.mehta@yahoo.in', '+91 98450 67890', true],
      ['Kavya Iyer', 'India', 'kavya.iyer@outlook.com', '+91 99000 11223', true],
      ['Rohan Kapoor', 'India', 'rohan.kapoor@rediffmail.com', '+91 98100 44556', false]
    ],
    // [code, name, gem, type, carat, colour, shape, treatment, certType, lab, cost, price, min, supplier, description]
    catalog: [
      ['GEM-00101', 'Royal Blue Sapphire', 'Blue Sapphire', 'Loose gem', 3.05, 'Royal Blue', 'Oval', 'Heated', 'lab', 'NGJA Gem Testing Laboratory', 620000, 950000, 820000, 'SUP-01', 'Vivid royal blue with excellent clarity, Ratnapura origin.'],
      ['GEM-00102', 'Golden Yellow Sapphire', 'Yellow Sapphire', 'Loose gem', 4.20, 'Golden Yellow', 'Cushion', 'Untreated', 'lab', 'GRS (GemResearch Swisslab)', 380000, 590000, 500000, 'SUP-01', 'Unheated golden yellow, eye clean.'],
      ['GEM-00103', 'Hot Pink Sapphire', 'Pink Sapphire', 'Loose gem', 1.65, 'Hot Pink', 'Oval', 'Heated', 'lab', 'NGJA Gem Testing Laboratory', 210000, 340000, 290000, 'SUP-01', 'Bright hot pink, very good cut.'],
      ['GEM-00104', 'Padparadscha Sapphire', 'Padparadscha', 'Loose gem', 1.42, 'Pinkish Orange', 'Oval', 'Untreated', 'lab', 'GRS (GemResearch Swisslab)', 820000, 1350000, 1150000, 'SUP-01', 'Rare unheated padparadscha with lotus-blossom colour.'],
      ['GEM-00105', 'Star Sapphire Cabochon', 'Star Sapphire', 'Loose gem', 6.80, 'Grey Blue', 'Cabochon', 'Natural', 'lab', 'NGJA Gem Testing Laboratory', 240000, 385000, 330000, 'SUP-04', 'Sharp six-ray star, centred.'],
      ['GEM-00106', 'Ceylon Ruby', 'Ruby', 'Loose gem', 1.10, 'Pigeon Red', 'Oval', 'Heated', 'shop', '', 160000, 260000, 220000, 'SUP-03', 'Pigeon-blood red, slight silk.'],
      ['GEM-00107', 'Chrysoberyl Cat\'s Eye', "Cat's Eye", 'Loose gem', 3.40, 'Honey', 'Cabochon', 'Natural', 'shop', '', 290000, 460000, 400000, 'SUP-04', 'Strong milk-and-honey chatoyancy.'],
      ['GEM-00108', 'Alexandrite', 'Alexandrite', 'Loose gem', 0.92, 'Green-Red', 'Cushion', 'Natural', 'shop', '', 350000, 560000, 480000, 'SUP-03', 'Clear colour change from green to raspberry red.'],
      ['GEM-00109', 'Blue Sheen Moonstone', 'Moonstone', 'Loose gem', 8.50, 'Blue Sheen', 'Cabochon', 'Natural', 'shop', '', 18000, 38000, 30000, 'SUP-04', 'Meetiyagoda moonstone with strong blue adularescence.'],
      ['GEM-00110', 'Lavender Spinel', 'Spinel', 'Loose gem', 2.30, 'Lavender', 'Oval', 'Natural', 'shop', '', 90000, 150000, 125000, 'SUP-03', 'Soft lavender, very bright.'],
      ['GEM-00111', 'Rhodolite Garnet', 'Garnet', 'Loose gem', 5.10, 'Deep Red', 'Round', 'Natural', 'shop', '', 15000, 32000, 26000, 'SUP-03', 'Deep red with purple flashes.'],
      ['GEM-00112', 'Sky Blue Topaz', 'Topaz', 'Loose gem', 7.60, 'Sky Blue', 'Emerald', 'Heated', 'shop', '', 12000, 28000, 22000, 'SUP-03', 'Emerald cut, clean.'],
      ['GEM-00113', 'Cornflower Sapphire Ring', 'Blue Sapphire', 'Ring', 1.85, 'Cornflower Blue', 'Oval', 'Heated', 'lab', 'NGJA Gem Testing Laboratory', 290000, 480000, 410000, 'SUP-02', '18k white gold solitaire with diamond shoulders.'],
      ['GEM-00114', 'Canary Sapphire Ring', 'Yellow Sapphire', 'Ring', 2.60, 'Canary Yellow', 'Cushion', 'Heated', 'lab', 'NGJA Gem Testing Laboratory', 180000, 295000, 250000, 'SUP-02', '22k yellow gold, halo setting.'],
      ['GEM-00115', 'Pink Sapphire Pendant', 'Pink Sapphire', 'Pendant', 1.20, 'Light Pink', 'Pear', 'Heated', 'lab', 'NGJA Gem Testing Laboratory', 130000, 215000, 185000, 'SUP-05', 'Pear drop on 18k rose gold chain.'],
      ['GEM-00116', 'Rainbow Moonstone Earrings', 'Moonstone', 'Earrings', 4.00, 'Rainbow', 'Cabochon', 'Natural', 'shop', '', 22000, 48000, 40000, 'SUP-05', 'Pair, sterling silver hooks (total 4.00 ct).'],
      ['GEM-00117', 'Ruby Halo Ring', 'Ruby', 'Ring', 0.85, 'Red', 'Oval', 'Heated', 'shop', '', 140000, 235000, 200000, 'SUP-02', '18k yellow gold with diamond halo.'],
      ['GEM-00118', 'Star Sapphire Pendant', 'Star Sapphire', 'Pendant', 4.30, 'Grey Blue', 'Cabochon', 'Natural', 'lab', 'NGJA Gem Testing Laboratory', 175000, 290000, 245000, 'SUP-05', 'Bezel set in sterling silver.'],
      ['GEM-00119', 'Garnet Stud Earrings', 'Garnet', 'Earrings', 3.20, 'Wine Red', 'Round', 'Natural', 'shop', '', 16000, 35000, 28000, 'SUP-05', 'Pair, 14k gold studs.'],
      ['GEM-00120', 'London Blue Topaz Necklace', 'Topaz', 'Necklace', 12.40, 'London Blue', 'Oval', 'Heated', 'shop', '', 45000, 95000, 80000, 'SUP-05', 'Five-stone necklace, sterling silver.'],
      ['GEM-00121', 'Red Spinel Ring', 'Spinel', 'Ring', 1.55, 'Red', 'Cushion', 'Natural', 'shop', '', 95000, 165000, 140000, 'SUP-02', '18k rose gold, cathedral setting.'],
      ['GEM-00122', 'Blue Sapphire Stud Earrings', 'Blue Sapphire', 'Earrings', 2.10, 'Royal Blue', 'Round', 'Heated', 'lab', 'GIA', 340000, 560000, 470000, 'SUP-02', 'Pair, 18k white gold (total 2.10 ct).'],
      ['GEM-00123', 'Cat\'s Eye Signet Ring', "Cat's Eye", 'Ring', 2.75, 'Honey', 'Cabochon', 'Natural', 'shop', '', 210000, 350000, 300000, 'SUP-04', 'Men\'s signet ring, 22k gold.'],
      ['GEM-00124', 'Alexandrite Pendant', 'Alexandrite', 'Pendant', 0.68, 'Green-Purple', 'Oval', 'Natural', 'shop', '', 240000, 395000, 340000, 'SUP-05', '18k white gold, colour change visible indoors.'],
      ['GEM-00125', 'Blue Sapphire Ring', 'Blue Sapphire', 'Ring', 2.15, 'Royal Blue', 'Oval', 'Heated', 'lab', 'NGJA Gem Testing Laboratory', 280000, 450000, 380000, 'SUP-02', 'Classic 18k white gold solitaire (BRD example product).'],
      ['GEM-00126', 'Padparadscha Ring', 'Padparadscha', 'Ring', 1.05, 'Sunset Pink', 'Cushion', 'Heated', 'lab', 'GRS (GemResearch Swisslab)', 520000, 860000, 740000, 'SUP-01', 'Platinum, diamond halo.'],
      ['GEM-00127', 'Moonstone Tennis Bracelet', 'Moonstone', 'Bracelet', 15.00, 'Blue Sheen', 'Cabochon', 'Natural', 'none', '', 28000, 62000, 52000, 'SUP-05', 'Sterling silver, 15 stones (total 15.00 ct).'],
      ['GEM-00128', 'Ceylon Blue Sapphire', 'Blue Sapphire', 'Loose gem', 1.20, 'Ceylon Blue', 'Cushion', 'Untreated', 'lab', 'SSEF', 250000, 420000, 360000, 'SUP-01', 'Untreated, classic Ceylon cornflower tone.'],
      ['GEM-00129', 'Lemon Sapphire Pendant', 'Yellow Sapphire', 'Pendant', 1.75, 'Lemon', 'Pear', 'Heated', 'lab', 'NGJA Gem Testing Laboratory', 95000, 165000, 140000, 'SUP-05', '14k yellow gold.'],
      ['GEM-00130', 'Pink Sapphire Earrings', 'Pink Sapphire', 'Earrings', 1.40, 'Light Pink', 'Round', 'Heated', 'lab', 'NGJA Gem Testing Laboratory', 150000, 245000, 210000, 'SUP-02', 'Pair, drop style, 18k rose gold.']
    ],
    // Templates for historical (already sold) items: [gem, type, caratMin, caratMax, colour, shape, treatment, priceMin, priceMax]
    soldTemplates: [
      ['Blue Sapphire', 'Ring', 1.2, 2.4, 'Royal Blue', 'Oval', 'Heated', 280000, 520000],
      ['Blue Sapphire', 'Loose gem', 0.9, 2.6, 'Cornflower Blue', 'Cushion', 'Heated', 180000, 600000],
      ['Yellow Sapphire', 'Pendant', 1.0, 2.5, 'Golden Yellow', 'Pear', 'Heated', 120000, 260000],
      ['Pink Sapphire', 'Ring', 0.8, 1.6, 'Hot Pink', 'Oval', 'Heated', 150000, 320000],
      ['Star Sapphire', 'Ring', 3.0, 6.0, 'Grey Blue', 'Cabochon', 'Natural', 160000, 340000],
      ['Ruby', 'Earrings', 0.8, 1.6, 'Pigeon Red', 'Round', 'Heated', 140000, 280000],
      ["Cat's Eye", 'Ring', 2.0, 4.0, 'Honey', 'Cabochon', 'Natural', 180000, 380000],
      ['Moonstone', 'Pendant', 3.0, 9.0, 'Blue Sheen', 'Cabochon', 'Natural', 22000, 55000],
      ['Moonstone', 'Earrings', 2.0, 6.0, 'Rainbow', 'Cabochon', 'Natural', 20000, 48000],
      ['Spinel', 'Loose gem', 1.0, 3.0, 'Red', 'Cushion', 'Natural', 80000, 190000],
      ['Garnet', 'Pendant', 2.0, 6.0, 'Deep Red', 'Oval', 'Natural', 18000, 42000],
      ['Topaz', 'Ring', 3.0, 8.0, 'Sky Blue', 'Emerald', 'Heated', 25000, 60000],
      ['Alexandrite', 'Ring', 0.5, 1.0, 'Green-Red', 'Cushion', 'Natural', 280000, 520000],
      ['Padparadscha', 'Pendant', 0.8, 1.3, 'Pinkish Orange', 'Oval', 'Heated', 420000, 780000]
    ]
  };

  /* ---------- deterministic random so the demo looks the same every reset ---------- */
  function rng(seed) { let s = seed >>> 0; return function () { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }

  function buildSeed() {
    const R = rng(20261110);
    const pick = a => a[Math.floor(R() * a.length)];
    const between = (a, b) => a + R() * (b - a);
    const round = (n, step) => Math.round(n / step) * step;
    const now = new Date();
    const dAgo = (days, h = 10, m = 0) => { const d = new Date(now); d.setDate(d.getDate() - days); d.setHours(h, m, 0, 0); return d.toISOString(); };
    const ymd = iso => iso.slice(0, 10);
    const raw = JSON.parse(JSON.stringify(SEED_RAW));

    const S = {
      version: 1,
      createdAt: now.toISOString(),
      shop: raw.shop,
      branches: raw.branches,
      users: raw.users.map(u => Object.assign(u, { lastLogin: dAgo(u.lastLogin, 8, 45) })),
      lists: raw.lists,
      commissionRules: raw.commissionRules,
      rates: { USD: raw.rates.USD, EUR: raw.rates.EUR, updatedAt: dAgo(0, 8, 30), by: 'u1' },
      rateHistory: [],
      suppliers: raw.suppliers,
      guides: raw.guides,
      customers: [],
      products: [],
      purchases: [],
      supplierReturns: [],
      sales: [],
      held: [],
      commissions: [],
      commissionPayments: [],
      quotes: [],
      returns: [],
      creditNotes: [],
      transfers: [],
      corrections: [],
      stockChecks: [],
      expenses: [],
      recurring: [],
      openings: {},
      closings: [],
      campaigns: [],
      approvals: [],
      alerts: [],
      log: [],
      months: {},
      messaging: {
        smtp: { enabled: true, host: 'smtp.gmail.com', port: 587, security: 'STARTTLS', user: 'sales@ceylongemhouse.lk', from: 'Ceylon Gem House <sales@ceylongemhouse.lk>', password: '••••••••••••' },
        ses: { enabled: true, domain: 'ceylongemhouse.lk', region: 'ap-south-1', spf: true, dkim: true },
        wa: { enabled: false, phone: '', accountId: '', token: '', verified: false },
        auto: { invoiceEmail: true, invoiceWhatsapp: true, thankYou: true, ownerClosing: true },
        emailOn: true
      },
      seq: {}
    };
    S.shop.ngjaExpiry = ymd(dAgo(-S.shop.ngjaExpiryDays));
    delete S.shop.ngjaExpiryDays;

    // exchange rate history (14 days)
    for (let i = 13; i >= 0; i--) {
      S.rateHistory.push({ date: ymd(dAgo(i)), USD: round(raw.rates.USD + between(-3, 3), 0.5), EUR: round(raw.rates.EUR + between(-4, 4), 0.5), by: 'u1' });
    }
    S.rateHistory[S.rateHistory.length - 1].USD = raw.rates.USD;
    S.rateHistory[S.rateHistory.length - 1].EUR = raw.rates.EUR;

    // customers
    raw.customers.forEach((c, i) => {
      S.customers.push({
        id: 'C-' + String(i + 1).padStart(3, '0'), name: c[0], country: c[1], email: c[2], whatsapp: c[3],
        consent: c[4], consentAt: c[4] ? dAgo(30 - i, 11) : null, unsubscribed: false,
        passport: (c[1].slice(0, 2).toUpperCase()) + String(4000000 + i * 7919).slice(0, 7), createdAt: dAgo(Math.max(0, 29 - i), 11), notes: ''
      });
    });

    const hist = (p, at, user, action, detail) => p.history.push({ at, user, action, detail });

    // purchases for catalog (in-stock) products: grouped by supplier
    const catalogBySup = {};
    raw.catalog.forEach(r => { (catalogBySup[r[13]] = catalogBySup[r[13]] || []).push(r); });
    let purN = 0;
    const purchaseDays = { 'SUP-01': [88, 21], 'SUP-02': [64, 9], 'SUP-03': [95], 'SUP-04': [72], 'SUP-05': [40, 5] };
    Object.keys(catalogBySup).forEach(sup => {
      const rows = catalogBySup[sup];
      const days = purchaseDays[sup];
      const chunks = days.length;
      const per = Math.ceil(rows.length / chunks);
      for (let c = 0; c < chunks; c++) {
        const part = rows.slice(c * per, (c + 1) * per);
        if (!part.length) continue;
        purN++;
        const pid = 'PUR-' + String(purN).padStart(4, '0');
        const at = dAgo(days[c], 14, 30);
        S.purchases.push({ id: pid, date: at, supplierId: sup, branch: 'ella', lines: part.map(r => ({ productId: r[0], cost: r[10] })), total: part.reduce((a, r) => a + r[10], 0), billPhoto: 'bill-' + pid + '.jpg', paid: 'Cash', by: 'u1', notes: '' });
        part.forEach(r => {
          const p = {
            id: r[0], name: r[1], gem: r[2], type: r[3], carat: r[4], colour: r[5], shape: r[6], treatment: r[7],
            description: r[14], cert: { type: r[8], lab: r[9], no: r[8] === 'lab' ? (r[9].slice(0, 3).toUpperCase().replace(/[^A-Z]/g, '') + '-' + (24000 + Number(r[0].slice(-3)) * 37)) : (r[8] === 'shop' ? 'CGH-SC-' + r[0].slice(-5) : ''), date: r[8] === 'lab' ? ymd(dAgo(days[c] + 6)) : '', scan: r[8] === 'lab' ? 'cert-' + r[0] + '.pdf' : '' },
            cost: r[10], price: r[11], min: r[12], branch: 'ella', status: 'in_stock', supplierId: sup, purchaseId: pid,
            photos: 3, addedAt: at, addedBy: 'u1', history: []
          };
          hist(p, at, 'u1', 'Added', 'Bought from ' + raw.suppliers.find(s => s.id === sup).name + ' (' + pid + ')');
          hist(p, dAgo(days[c] - 0, 16, 10), 'u1', 'Tag printed', 'QR/barcode tag printed');
          S.products.push(p);
        });
      }
    });

    // historical sold products
    const soldProducts = [];
    for (let i = 0; i < 56; i++) {
      const t = raw.soldTemplates[i % raw.soldTemplates.length];
      const price = round(between(t[7], t[8]), 5000);
      const cost = round(price * between(0.58, 0.66), 1000);
      const min = round(price * 0.85, 1000);
      const code = 'GEM-' + String(44 + i).padStart(5, '0');
      const isSapphire = /Sapphire|Padparadscha/.test(t[0]);
      const p = {
        id: code, name: (t[1] === 'Loose gem' ? '' : '') + t[0] + (t[1] === 'Loose gem' ? '' : ' ' + t[1]), gem: t[0], type: t[1], carat: Math.round(between(t[2], t[3]) * 100) / 100,
        colour: t[4], shape: t[5], treatment: t[6], description: 'Sold item (demo history).',
        cert: isSapphire ? { type: 'lab', lab: 'NGJA Gem Testing Laboratory', no: 'NGJ-' + (21000 + i * 41), date: ymd(dAgo(120)), scan: 'cert-' + code + '.pdf' } : { type: 'shop', lab: '', no: 'CGH-SC-' + code.slice(-5), date: '', scan: '' },
        cost, price, min, branch: 'ella', status: 'sold', supplierId: raw.suppliers[i % 5].id, purchaseId: null, photos: 2, addedAt: null, addedBy: 'u1', history: []
      };
      soldProducts.push(p);
    }
    // older purchases for sold products
    for (let k = 0; k < 6; k++) {
      purN++;
      const pid = 'PUR-' + String(purN).padStart(4, '0');
      const at = dAgo(150 - k * 18, 13, 0);
      const part = soldProducts.filter((p, i) => i % 6 === k);
      const sup = raw.suppliers[k % 5].id;
      part.forEach(p => { p.purchaseId = pid; p.supplierId = sup; p.addedAt = at; hist(p, at, 'u1', 'Added', 'Bought from ' + raw.suppliers[k % 5].name + ' (' + pid + ')'); });
      S.purchases.push({ id: pid, date: at, supplierId: sup, branch: 'ella', lines: part.map(p => ({ productId: p.id, cost: p.cost })), total: part.reduce((a, p) => a + p.cost, 0), billPhoto: 'bill-' + pid + '.jpg', paid: 'Cash', by: 'u1', notes: '' });
    }
    S.products = soldProducts.concat(S.products);
    S.purchases.sort((a, b) => a.date.localeCompare(b.date));

    // product returned to supplier (B4)
    const rp = { id: 'GEM-00100', name: 'Pink Spinel', gem: 'Spinel', type: 'Loose gem', carat: 1.8, colour: 'Light Pink', shape: 'Oval', treatment: 'Natural', description: 'Returned to supplier: inclusion not disclosed.', cert: { type: 'shop', lab: '', no: '', date: '', scan: '' }, cost: 70000, price: 120000, min: 100000, branch: 'ella', status: 'returned_supplier', supplierId: 'SUP-03', purchaseId: 'PUR-0003', photos: 1, addedAt: dAgo(95, 14, 30), addedBy: 'u1', history: [] };
    hist(rp, dAgo(95, 14, 30), 'u1', 'Added', 'Bought from Elahera Mining Co-op (PUR-0003)');
    hist(rp, dAgo(60, 11, 0), 'u1', 'Returned to supplier', 'Visible inclusion not disclosed at purchase (SR-0001)');
    S.products.push(rp);
    const pur3 = S.purchases.find(p => p.supplierId === 'SUP-03');
    if (pur3) { pur3.lines.push({ productId: rp.id, cost: rp.cost }); pur3.total += rp.cost; rp.purchaseId = pur3.id; }
    S.supplierReturns.push({ id: 'SR-0001', supplierId: 'SUP-03', productId: rp.id, reason: 'Visible inclusion not disclosed at purchase', refund: 70000, refundMethod: 'Cash', date: dAgo(60, 11, 0), by: 'u1' });

    // ---------- sales: ~40 across the last 30 days ----------
    const P = id => S.products.find(p => p.id === id);
    const plan = [];
    for (let i = 0; i < 40; i++) {
      let day = Math.floor(29 - i * 0.74);
      if (i >= 37) day = 0; // three sales today
      plan.push(day);
    }
    let soldIdx = 0, invNo = 100;
    const payMix = ['card', 'card', 'card', 'cashUSD', 'cashUSD', 'cashEUR', 'cashLKR', 'split', 'split', 'card'];
    plan.forEach((day, i) => {
      const nItems = R() < 0.25 && soldIdx < soldProducts.length - 1 ? 2 : 1;
      const items = [];
      for (let k = 0; k < nItems && soldIdx < soldProducts.length; k++) {
        const p = soldProducts[soldIdx++];
        let agreed = round(p.price * between(0.86, 1), 1000);
        if (agreed < p.min) agreed = p.min;
        items.push({ productId: p.id, listPrice: p.price, agreed, minPrice: p.min, approvalId: null });
      }
      const at = dAgo(day, 10 + Math.floor(R() * 7), Math.floor(R() * 60));
      const guideId = R() < 0.7 ? pick(raw.guides).id : null;
      const cust = S.customers[(i * 7) % S.customers.length];
      invNo++;
      const sale = {
        id: 'INV-' + String(invNo).padStart(6, '0'), date: at, branch: 'ella', customerId: cust.id, guideId, staffId: R() < 0.85 ? 'u2' : 'u1',
        items, payments: [], status: 'completed', sentEmail: true, sentWhatsapp: R() < 0.5, notes: ''
      };
      S.sales.push(sale);
    });
    // a sale containing GEM-00121 that was later returned (E1/E2 history)
    const retSale = S.sales[9];
    const g121 = P('GEM-00121');

    // compute totals, payments, commissions using Calc from app.js
    const rates = S.rates;
    S.sales.forEach((s, i) => {
      const t = Calc.saleTotals(s.items, s.guideId, S);
      Object.assign(s, { subtotal: t.subtotal, net: t.net, vat: t.vat, total: t.total, guidePct: t.guidePct, guideComm: t.guideComm, staffComm: t.staffComm });
      const mode = payMix[i % payMix.length];
      if (mode === 'card') s.payments.push({ method: 'card', currency: 'LKR', amount: t.total, rate: 1, lkr: t.total });
      else if (mode === 'split') {
        const usd = Math.round(t.total * 0.4 / rates.USD / 50) * 50 || 50;
        s.payments.push({ method: 'cash', currency: 'USD', amount: usd, rate: rates.USD, lkr: usd * rates.USD });
        s.payments.push({ method: 'card', currency: 'LKR', amount: t.total - usd * rates.USD, rate: 1, lkr: t.total - usd * rates.USD });
      } else {
        const cur = mode.slice(4);
        const rate = cur === 'LKR' ? 1 : rates[cur];
        const amt = cur === 'LKR' ? t.total : Math.ceil(t.total / rate / 10) * 10;
        s.payments.push({ method: 'cash', currency: cur, amount: amt, rate, lkr: amt * rate });
        s.change = amt * rate - t.total;
      }
      s.items.forEach(it => {
        const p = P(it.productId);
        p.status = 'sold'; p.saleId = s.id; p.soldAt = s.date;
        hist(p, s.date, s.staffId, 'Sold', 'Invoice ' + s.id + ' to ' + S.customers.find(c => c.id === s.customerId).name);
      });
      const old = (now - new Date(s.date)) / 864e5 > 7;
      if (s.guideId) S.commissions.push({ id: 'CM-' + s.id.slice(-4) + 'G', saleId: s.id, payee: 'guide', payeeId: s.guideId, amount: t.guideComm, pct: t.guidePct, status: old ? 'paid' : 'unpaid', paidAt: old ? s.date : null, method: old ? 'Cash (owner)' : null, date: s.date });
      S.commissions.push({ id: 'CM-' + s.id.slice(-4) + 'S', saleId: s.id, payee: 'staff', payeeId: s.staffId, amount: t.staffComm, status: old && (now - new Date(s.date)) / 864e5 > 14 ? 'paid' : 'unpaid', paidAt: old ? s.date : null, method: old ? 'With salary' : null, date: s.date });
    });

    // returned sale (E1/E2): replace first item with GEM-00121 history
    if (retSale && g121) {
      hist(g121, retSale.date, 'u2', 'Sold', 'Invoice ' + retSale.id);
      const retAt = dAgo(Math.max(1, Math.round((now - new Date(retSale.date)) / 864e5) - 3), 15, 20);
      hist(g121, retAt, 'u1', 'Returned', 'Customer return approved, credit note CN-0001');
      g121.status = 'returned';
      S.returns.push({ id: 'RT-0001', saleId: retSale.id, productId: g121.id, kind: 'refund', reason: 'Ring size too small, no resizing possible', status: 'approved', at: retAt, by: 'u2', approvalId: 'AP-0001', creditNoteId: 'CN-0001', amount: 165000 });
      S.creditNotes.push({ id: 'CN-0001', saleId: retSale.id, returnId: 'RT-0001', productId: g121.id, amount: 165000, vat: Calc.split(165000, S).vat, date: retAt, by: 'u1', commissionReversed: true });
      S.approvals.push({ id: 'AP-0001', type: 'return', status: 'approved', at: retAt, requestedBy: 'u2', branch: 'ella', title: 'Return: Red Spinel Ring (GEM-00121)', detail: 'Ring size too small, no resizing possible', amount: 165000, payload: { returnId: 'RT-0001' }, decidedAt: retAt, decidedBy: 'u1', note: 'OK, refund by card' });
      // add GEM-00121 as an extra line on that sale (it was part of the bill)
      retSale.items.push({ productId: g121.id, listPrice: 165000, agreed: 165000, minPrice: 140000, returned: true });
      const t = Calc.saleTotals(retSale.items, retSale.guideId, S);
      Object.assign(retSale, { subtotal: t.subtotal, net: t.net, vat: t.vat, total: t.total, guideComm: t.guideComm, staffComm: t.staffComm });
      retSale.payments = [{ method: 'card', currency: 'LKR', amount: t.total, rate: 1, lkr: t.total }];
      retSale.change = 0;
      S.commissions = S.commissions.filter(c => c.saleId !== retSale.id);
      if (retSale.guideId) S.commissions.push({ id: 'CM-' + retSale.id.slice(-4) + 'G', saleId: retSale.id, payee: 'guide', payeeId: retSale.guideId, amount: t.guideComm, pct: t.guidePct, status: 'paid', paidAt: retSale.date, method: 'Cash (owner)', date: retSale.date });
      S.commissions.push({ id: 'CM-' + retSale.id.slice(-4) + 'S', saleId: retSale.id, payee: 'staff', payeeId: retSale.staffId, amount: t.staffComm, status: 'paid', paidAt: retSale.date, method: 'With salary', date: retSale.date });
      const rev = Calc.saleTotals([{ agreed: 165000 }], retSale.guideId, S);
      if (retSale.guideId) S.commissions.push({ id: 'CM-RT01G', saleId: retSale.id, payee: 'guide', payeeId: retSale.guideId, amount: -rev.guideComm, status: 'reversed', date: retAt, note: 'Reversed – return RT-0001' });
    }

    // reservations & quotations (F7)
    S.quotes.push({ id: 'RS-0001', kind: 'reservation', items: [{ productId: 'GEM-00118', price: 275000 }], customerId: 'C-013', created: dAgo(2, 15), expires: ymd(dAgo(-3)), deposit: 30000, status: 'active', by: 'u2', branch: 'ella', notes: 'Coming back Friday with husband' });
    S.quotes.push({ id: 'RS-0002', kind: 'reservation', items: [{ productId: 'GEM-00126', price: 820000 }], customerId: 'C-020', created: dAgo(1, 12), expires: ymd(dAgo(-2)), deposit: 0, status: 'active', by: 'u2', branch: 'ella', notes: '' });
    S.quotes.push({ id: 'QT-0001', kind: 'quotation', items: [{ productId: 'GEM-00101', price: 900000 }, { productId: 'GEM-00122', price: 540000 }], customerId: 'C-012', created: dAgo(3, 16), expires: ymd(dAgo(-11)), deposit: 0, status: 'active', by: 'u2', branch: 'ella', notes: 'Customer comparing with Colombo shop' });
    S.quotes.push({ id: 'QT-0002', kind: 'quotation', items: [{ productId: 'GEM-00108', price: 540000 }], customerId: 'C-022', created: dAgo(12, 11), expires: ymd(dAgo(2)), deposit: 0, status: 'expired', by: 'u1', branch: 'ella', notes: '' });
    P('GEM-00118').status = 'reserved'; P('GEM-00126').status = 'reserved';
    hist(P('GEM-00118'), dAgo(2, 15), 'u2', 'Reserved', 'RS-0001 for Jessica Miller until ' + ymd(dAgo(-3)));
    hist(P('GEM-00126'), dAgo(1, 12), 'u2', 'Reserved', 'RS-0002 for Emma Jansen');

    // branch transfer in transit (D3) — pre-stocking Branch 2
    S.transfers.push({ id: 'TR-0001', from: 'ella', to: 'b2', items: ['GEM-00109', 'GEM-00112', 'GEM-00119'], sentAt: dAgo(2, 17), sentBy: 'u1', status: 'in_transit', receivedAt: null, receivedBy: null, notes: 'Pre-stock for Branch 2 display' });
    ['GEM-00109', 'GEM-00112', 'GEM-00119'].forEach(id => { P(id).status = 'in_transit'; hist(P(id), dAgo(2, 17), 'u1', 'Moved', 'Sent Ella → Branch 2 (TR-0001)'); });
    S.stockChecks.push({ id: 'SCK-0001', date: dAgo(7, 18), branch: 'ella', by: 'u3', expected: 27, found: 26, missing: ['GEM-00116'], wrong: [], resolved: true, notes: 'GEM-00116 found later in the repair tray' });

    // held below-minimum sale waiting for owner approval (F3)
    S.held.push({ id: 'HS-0001', at: dAgo(0, 11, 42), branch: 'ella', staffId: 'u2', customer: { id: 'C-001' }, guideId: 'G-01', items: [{ productId: 'GEM-00113', listPrice: 480000, agreed: 395000, minPrice: 410000 }], status: 'pending', approvalId: 'AP-0010' });
    P('GEM-00113').status = 'on_hold';
    hist(P('GEM-00113'), dAgo(0, 11, 42), 'u2', 'On hold', 'Below-minimum price LKR 395,000 – waiting for owner approval (AP-0010)');
    S.approvals.push({ id: 'AP-0010', type: 'below_min', status: 'pending', at: dAgo(0, 11, 42), requestedBy: 'u2', branch: 'ella', title: 'Below minimum: Cornflower Sapphire Ring (GEM-00113)', detail: 'Agreed LKR 395,000 · Minimum LKR 410,000 · List LKR 480,000. Customer Anna Müller (Germany), guide Sunil Kumara.', amount: 395000, payload: { heldId: 'HS-0001' } });

    // pending return request (E1)
    const recent = S.sales.find(s => (now - new Date(s.date)) / 864e5 < 4 && (now - new Date(s.date)) / 864e5 >= 1) || S.sales[S.sales.length - 4];
    const rpId = recent.items[0].productId;
    S.returns.push({ id: 'RT-0002', saleId: recent.id, productId: rpId, kind: 'exchange', reason: 'Customer wants a different colour – exchange', status: 'pending', at: dAgo(0, 9, 20), by: 'u2', approvalId: 'AP-0011', amount: recent.items[0].agreed });
    S.approvals.push({ id: 'AP-0011', type: 'return', status: 'pending', at: dAgo(0, 9, 20), requestedBy: 'u2', branch: 'ella', title: 'Return/exchange: ' + P(rpId).name + ' (' + rpId + ')', detail: 'Invoice ' + recent.id + ' · Customer wants a different colour – exchange', amount: recent.items[0].agreed, payload: { returnId: 'RT-0002' } });

    // pending invoice cancellation (G6)
    const todaySale = S.sales[S.sales.length - 1];
    S.approvals.push({ id: 'AP-0012', type: 'cancel_invoice', status: 'pending', at: dAgo(0, 13, 5), requestedBy: 'u2', branch: 'ella', title: 'Cancel invoice ' + todaySale.id, detail: 'Wrong customer selected – invoice to be re-issued', amount: todaySale.total, payload: { saleId: todaySale.id, reason: 'Wrong customer selected – invoice to be re-issued' } });
    todaySale.cancelRequested = 'AP-0012';

    // pending stock correction (D7)
    S.corrections.push({ id: 'SC-0001', productId: 'GEM-00111', kind: 'carat', field: 'carat', from: 5.10, to: 5.01, reason: 'Re-weighed on calibrated scale: 5.01 ct, data entry error', status: 'approved', at: dAgo(20, 12), by: 'u3', approvalId: 'AP-0002' });
    S.approvals.push({ id: 'AP-0002', type: 'stock_correction', status: 'approved', at: dAgo(20, 12), requestedBy: 'u3', branch: 'ella', title: 'Stock correction: GEM-00111 carat 5.10 → 5.01', detail: 'Re-weighed on calibrated scale', payload: { correctionId: 'SC-0001' }, decidedAt: dAgo(20, 13), decidedBy: 'u1', note: '' });
    S.corrections.push({ id: 'SC-0002', productId: 'GEM-00116', kind: 'status', field: 'status', from: 'in_stock', to: 'written_off', reason: 'One earring clasp broken beyond repair – write off pair', status: 'pending', at: dAgo(0, 10, 15), by: 'u3', approvalId: 'AP-0013' });
    S.approvals.push({ id: 'AP-0013', type: 'stock_correction', status: 'pending', at: dAgo(0, 10, 15), requestedBy: 'u3', branch: 'ella', title: 'Stock correction: GEM-00116 → Write off', detail: 'One earring clasp broken beyond repair – write off pair', amount: 22000, payload: { correctionId: 'SC-0002' } });

    // ---------- expenses (M) ----------
    let exN = 0;
    const addExp = (daysAgo, cat, amount, vat, paidTo, method, extra) => {
      exN++;
      S.expenses.push(Object.assign({ id: 'EXP-' + String(exN).padStart(4, '0'), date: dAgo(daysAgo, 12), branch: 'ella', category: cat, amount, vat, paidTo, method, billPhoto: 'bill-exp-' + exN + '.jpg', status: 'approved', by: 'u4', note: '', recurringId: null }, extra || {}));
    };
    S.recurring.push({ id: 'RC-01', category: 'Rent', amount: 85000, vat: 0, paidTo: 'Mr. S. Rajapaksha (landlord)', method: 'Bank transfer', day: 1, branch: 'ella', active: true, lastRun: null });
    S.recurring.push({ id: 'RC-02', category: 'Phone & internet', amount: 6500, vat: 990, paidTo: 'SLT-Mobitel', method: 'Card', day: 5, branch: 'ella', active: true, lastRun: null });
    S.recurring.push({ id: 'RC-03', category: 'Staff salaries & allowances', amount: 110000, vat: 0, paidTo: 'Staff (2)', method: 'Bank transfer', day: 28, branch: 'ella', active: true, lastRun: null });
    const monthStarts = [2, 1, 0].map(k => { const d = new Date(now.getFullYear(), now.getMonth() - k, 1, 12); return d; });
    monthStarts.forEach((ms, mi) => {
      const da = d => Math.round((now - new Date(ms.getFullYear(), ms.getMonth(), d, 12)) / 864e5);
      const ok = d => da(d) >= 0;
      if (ok(1)) addExp(da(1), 'Rent', 85000, 0, 'Mr. S. Rajapaksha (landlord)', 'Bank transfer', { recurringId: 'RC-01', by: 'u1' });
      if (ok(5)) addExp(da(5), 'Phone & internet', 6500, 990, 'SLT-Mobitel', 'Card', { recurringId: 'RC-02' });
      if (ok(8)) addExp(da(8), 'Electricity', 15400 + mi * 900, 0, 'Ceylon Electricity Board', 'Card');
      if (ok(9)) addExp(da(9), 'Water', 2450, 0, 'NWSDB', 'Cash (drawer)');
      if (ok(12)) addExp(da(12), 'Packaging & gift boxes', 11800, 1800, 'Lanka Box Makers (Pvt) Ltd', 'Bank transfer');
      if (ok(15)) addExp(da(15), 'Lab & certificate fees', 23600, 3600, 'NGJA Gem Testing Laboratory', 'Bank transfer');
      if (ok(18)) addExp(da(18), 'Marketing & messages', 5900, 900, 'Meta (WhatsApp messages)', 'Card');
      if (ok(20)) addExp(da(20), 'Transport', 3200, 0, 'Bus/train to Ratnapura', 'Cash (drawer)');
      if (ok(28)) addExp(da(28), 'Staff salaries & allowances', 110000, 0, 'Staff (2)', 'Bank transfer', { recurringId: 'RC-03', by: 'u1' });
    });
    S.recurring.forEach(r => { const last = S.expenses.filter(e => e.recurringId === r.id).pop(); r.lastRun = last ? last.date : null; });
    addExp(0, 'Other', 1200, 0, 'Tea & snacks for customers', 'Cash (drawer)', { by: 'u2' });
    addExp(0, 'Transport', 800, 0, 'Tuk-tuk to post office', 'Cash (drawer)', { by: 'u2' });
    addExp(1, 'Repairs', 78000, 11898, 'Ella Glass & Aluminium', 'Bank transfer', { status: 'pending', approvalId: 'AP-0014', note: 'Display cabinet glass replacement', by: 'u4' });
    S.approvals.push({ id: 'AP-0014', type: 'expense', status: 'pending', at: dAgo(1, 12), requestedBy: 'u4', branch: 'ella', title: 'Large expense: Repairs LKR 78,000.00', detail: 'Display cabinet glass replacement – Ella Glass & Aluminium (limit LKR 50,000)', amount: 78000, payload: { expenseId: S.expenses[S.expenses.length - 1].id } });

    // ---------- opening cash & closings (I) ----------
    const key = (d, b) => d + '|' + b;
    for (let day = 10; day >= 0; day--) {
      const d = ymd(dAgo(day));
      S.openings[key(d, 'ella')] = { LKR: 25000, USD: 100, EUR: 50, at: dAgo(day, 8, 55), by: 'u2' };
    }
    for (let day = 10; day >= 1; day--) {
      const d = ymd(dAgo(day));
      const exp = Calc.expectedCash(S, d, 'ella');
      const counted = { LKR: exp.cash.LKR, USD: exp.cash.USD, EUR: exp.cash.EUR };
      if (day === 4) counted.LKR -= 2500;
      if (day === 7) counted.USD -= 20;
      S.closings.push({ id: 'DC-' + d.replace(/-/g, ''), date: d, branch: 'ella', expected: exp.cash, counted, cardExpected: exp.card, cardCounted: exp.card, by: 'u2', at: dAgo(day, 18, 10), sentToOwner: true, note: day === 4 ? 'Short by LKR 2,500 – guide paid in cash, slip missing' : (day === 7 ? 'USD 20 short – checking' : '') });
    }

    // ---------- campaigns (J) ----------
    S.campaigns.push({ id: 'CP-0001', channel: 'email', name: 'New Padparadscha arrivals', subject: 'Rare Padparadscha sapphires just arrived in Ella', body: 'Dear {name},\n\nThank you for visiting Ceylon Gem House. We have just received a small collection of unheated padparadscha sapphires...', image: 'Padparadscha', audience: { countries: ['Germany', 'Netherlands', 'United Kingdom'], gems: [] }, sentAt: dAgo(18, 10), by: 'u1', status: 'sent', stats: { sent: 7, delivered: 7, opened: 4, clicked: 2, unsubscribed: 0 } });
    S.campaigns.push({ id: 'CP-0002', channel: 'whatsapp', name: 'Christmas gift offer', subject: '', body: 'Hello {name}! 🎄 Season\'s greetings from Ceylon Gem House, Ella. 10% off moonstone jewellery until 31/12. Reply STOP to unsubscribe.', image: 'Moonstone', audience: { countries: [], gems: ['Moonstone', 'Blue Sapphire'] }, sentAt: dAgo(6, 9), by: 'u1', status: 'sent', stats: { sent: 12, delivered: 11, opened: 9, clicked: 0, unsubscribed: 1 } });
    S.campaigns.push({ id: 'CP-0003', channel: 'email', name: 'Sri Lanka gem guide (draft)', subject: 'Your guide to Ceylon sapphires', body: 'Dear {name}, ...', image: 'Blue Sapphire', audience: { countries: [], gems: [] }, sentAt: null, by: 'u1', status: 'draft', stats: { sent: 0, delivered: 0, opened: 0, clicked: 0, unsubscribed: 0 } });

    // ---------- month locks (N6) ----------
    const mk = k => { const d = new Date(now.getFullYear(), now.getMonth() - k, 1); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0'); };
    S.months[mk(3)] = { status: 'closed', closedAt: dAgo(70), by: 'u4' };
    S.months[mk(2)] = { status: 'closed', closedAt: dAgo(38), by: 'u4' };

    // ---------- activity log & alerts (K) ----------
    S.sales.forEach(s => {
      const cust = S.customers.find(c => c.id === s.customerId);
      S.log.push({ at: s.date, user: s.staffId, action: 'Sale completed', detail: s.id + ' · ' + cust.name + ' · LKR ' + s.total.toLocaleString('en-US'), branch: s.branch });
      S.alerts.push({ id: 'AL-' + s.id, at: s.date, type: 'sale', text: 'Sale ' + s.id + ' · LKR ' + Math.round(s.total).toLocaleString('en-US') + ' · ' + cust.country, read: (now - new Date(s.date)) / 864e5 > 1, link: 'g-payments/invoice.html?id=' + s.id, branch: s.branch });
    });
    S.purchases.forEach(p => S.log.push({ at: p.date, user: 'u1', action: 'Purchase recorded', detail: p.id + ' · ' + S.suppliers.find(s => s.id === p.supplierId).name + ' · ' + p.lines.length + ' items', branch: p.branch }));
    S.closings.forEach(c => S.log.push({ at: c.at, user: c.by, action: 'Day closed', detail: c.id, branch: c.branch }));
    S.log.push({ at: dAgo(0, 8, 30), user: 'u1', action: 'Exchange rates updated', detail: 'USD 300.00 · EUR 325.00', branch: 'all' });
    S.log.push({ at: dAgo(2, 17), user: 'u1', action: 'Transfer sent', detail: 'TR-0001 Ella → Branch 2 · 3 items', branch: 'ella' });
    S.approvals.filter(a => a.status === 'pending').forEach(a => {
      S.log.push({ at: a.at, user: a.requestedBy, action: 'Approval requested', detail: a.title, branch: a.branch });
      S.alerts.push({ id: 'AL-' + a.id, at: a.at, type: a.type, text: a.title, read: false, link: 'k-owner-control/approvals.html', branch: a.branch });
    });
    S.closings.slice(-3).forEach(c => {
      const d = Calc.closingDiff(c, S);
      S.alerts.push({ id: 'AL-' + c.id, at: c.at, type: 'closing', text: 'Day closing ' + c.date.split('-').reverse().join('/') + (Math.abs(d) > 0.5 ? ' · difference LKR ' + d.toLocaleString('en-US') : ' · balanced'), read: true, link: 'l-reports/daily-closing.html', branch: c.branch });
    });
    S.alerts.push({ id: 'AL-NGJA', at: dAgo(0, 7), type: 'licence', text: 'NGJA dealer licence expires in ' + 45 + ' days – renew before ' + S.shop.ngjaExpiry.split('-').reverse().join('/'), read: false, link: 'a-setup/shop-vat.html', branch: 'all' });
    S.log.sort((a, b) => b.at.localeCompare(a.at));
    S.alerts.sort((a, b) => b.at.localeCompare(a.at));

    S.seq = { invoice: invNo, product: 130, purchase: purN, approval: 14, expense: exN, alert: 0, quote: 2, transfer: 1, ret: 2, credit: 1, correction: 2, campaign: 3, supplierReturn: 1, held: 1, payment: 0, stockCheck: 1, customer: S.customers.length, guide: 6, supplier: 5, user: 4 };
    return S;
  }

  window.SEED_RAW = SEED_RAW;
  window.buildSeed = buildSeed;
})();

-- =============================================================================
-- V10: reference catalog seed (platform.ref_*), copied into each tenant at
-- provisioning (V11). URS-MD-006/007.
-- Condition and time-point sets follow ICH Q1A(R2) / Q1B / Q5C conventions.
-- Review by the stability SME before release (VERIFY flag in docs/data/seed-data.md).
-- =============================================================================

INSERT INTO platform.ref_dictionary (code, name, category, legacy_code, allow_tenant_values, description) VALUES
 ('STORAGE_CONDITION', 'Storage condition',          'STABILITY',    'STUDY_CONDITION',   true,  'ICH and custom storage conditions'),
 ('STUDY_REASON',      'Reason for study',            'STABILITY',    'REASON_FOR_STUDY',  true,  NULL),
 ('STUDY_LENGTH',      'Study length',                'STABILITY',    'STUDY_LENGTH',      true,  NULL),
 ('STORAGE_LOCATION',  'Storage location (chamber)',  'ORGANISATION', 'STORAGE_LOCATION',  true,  'Site-specific; empty at provisioning'),
 ('ANALYST',           'Analyst',                     'ORGANISATION', 'ANALYST',           true,  'Used for migrated data; native data references users'),
 ('LAB_NUMBER',        'Lab number',                  'ORGANISATION', 'LAB_NUMBER',        true,  NULL),
 ('MEASUREMENT',       'Unit of measure',             'RESULTS',      'MEASUREMENT',       true,  NULL),
 ('ABBREVIATION',      'Result abbreviation',         'RESULTS',      'ABBREVIATION',      true,  'Maps to structured qualifiers'),
 ('QUICKLIST',         'Text result quick list',      'RESULTS',      'QUICKLIST',         true,  NULL),
 ('DOSAGE_FORM',       'Dosage form',                 'STABILITY',    NULL,                true,  NULL),
 ('PACKAGE_SIZE',      'Package size / count',        'PACKAGING',    'PACKAGE_SIZE',      true,  NULL),
 ('CONTAINER',         'Container',                   'PACKAGING',    'CONTAINER',         true,  NULL),
 ('CONTAINER_RESIN',   'Container resin / material',  'PACKAGING',    'CONTAINER_RESIN',   true,  NULL),
 ('CLOSURE',           'Closure',                     'PACKAGING',    'CLOSURE',           true,  NULL),
 ('LINER',             'Liner',                       'PACKAGING',    'LINER',             true,  NULL),
 ('INNERSEAL',         'Inner seal',                  'PACKAGING',    'INNERSEAL',         true,  NULL),
 ('DESICCANT',         'Desiccant',                   'PACKAGING',    'DESICCANT',         true,  NULL),
 ('FILLER',            'Filler (coil)',               'PACKAGING',    'FILLER',            true,  NULL),
 ('BLISTER',           'Blister',                     'PACKAGING',    'BLISTERS',          true,  NULL),
 ('LIDDING_FOIL',      'Lidding foil',                'PACKAGING',    'LIDDING_FOIL',      true,  NULL),
 ('FORMING_FOIL',      'Forming foil / film',         'PACKAGING',    'FORMING_FOIL',      true,  NULL),
 ('OTHER_COMPONENT',   'Other packaging component',   'PACKAGING',    'OTHER',             true,  NULL),
 ('SUPPLIER',          'Supplier / manufacturer',     'PACKAGING',    'SUPPLIER',          true,  'Component and raw-material suppliers; empty at provisioning'),
 ('PRODUCTION_SITE',   'Manufacturing / packaging site', 'ORGANISATION', 'SITE',          true,  'Empty at provisioning'),
 ('CHANGE_REASON',     'Reason for change',           'SYSTEM',       NULL,                true,  'Coded reasons for audit trail (URS-AUD-004)');

-- ---------------------------------------------------------------- conditions
INSERT INTO platform.ref_dictionary_value (dictionary_code, code, label, sort_order, attributes) VALUES
 ('STORAGE_CONDITION','25C60RH',   '25 °C ± 2 °C / 60 % RH ± 5 % RH (long-term, Zone I/II)', 10, '{"temp_c":25,"temp_tol":2,"rh":60,"rh_tol":5,"role":"LONG_TERM"}'),
 ('STORAGE_CONDITION','30C65RH',   '30 °C ± 2 °C / 65 % RH ± 5 % RH (intermediate; long-term Zone III/IVa)', 20, '{"temp_c":30,"temp_tol":2,"rh":65,"rh_tol":5,"role":"INTERMEDIATE"}'),
 ('STORAGE_CONDITION','30C75RH',   '30 °C ± 2 °C / 75 % RH ± 5 % RH (long-term, Zone IVb)', 30, '{"temp_c":30,"temp_tol":2,"rh":75,"rh_tol":5,"role":"LONG_TERM"}'),
 ('STORAGE_CONDITION','40C75RH',   '40 °C ± 2 °C / 75 % RH ± 5 % RH (accelerated)', 40, '{"temp_c":40,"temp_tol":2,"rh":75,"rh_tol":5,"role":"ACCELERATED"}'),
 ('STORAGE_CONDITION','5C',        '5 °C ± 3 °C (refrigerated, long-term)', 50, '{"temp_c":5,"temp_tol":3,"role":"LONG_TERM"}'),
 ('STORAGE_CONDITION','25C60RH_REF','25 °C ± 2 °C / 60 % RH ± 5 % RH (accelerated for refrigerated products)', 60, '{"temp_c":25,"temp_tol":2,"rh":60,"rh_tol":5,"role":"ACCELERATED"}'),
 ('STORAGE_CONDITION','M20C',      '−20 °C ± 5 °C (freezer, long-term)', 70, '{"temp_c":-20,"temp_tol":5,"role":"LONG_TERM"}'),
 ('STORAGE_CONDITION','5C_FRZ_ACC','5 °C ± 3 °C (accelerated for frozen products)', 75, '{"temp_c":5,"temp_tol":3,"role":"ACCELERATED"}'),
 ('STORAGE_CONDITION','25C40RH',   '25 °C ± 2 °C / 40 % RH ± 5 % RH (long-term, semi-permeable containers)', 80, '{"temp_c":25,"temp_tol":2,"rh":40,"rh_tol":5,"role":"LONG_TERM"}'),
 ('STORAGE_CONDITION','30C35RH',   '30 °C ± 2 °C / 35 % RH ± 5 % RH (intermediate, semi-permeable containers)', 85, '{"temp_c":30,"temp_tol":2,"rh":35,"rh_tol":5,"role":"INTERMEDIATE"}'),
 ('STORAGE_CONDITION','40C25RH',   '40 °C ± 2 °C / NMT 25 % RH (accelerated, semi-permeable containers)', 90, '{"temp_c":40,"temp_tol":2,"rh_max":25,"role":"ACCELERATED"}'),
 ('STORAGE_CONDITION','PHOTO_Q1B', 'Photostability per ICH Q1B (≥ 1.2 million lux·h and ≥ 200 W·h/m² near-UV)', 100, '{"role":"PHOTOSTABILITY"}'),
 ('STORAGE_CONDITION','M70C',      '−70 °C or below (ultra-low, biologics)', 110, '{"temp_c":-70,"role":"LONG_TERM"}');

INSERT INTO platform.ref_dictionary_value (dictionary_code, code, label, sort_order) VALUES
 ('STUDY_REASON','REGISTRATION',   'Registration (NDA/ANDA/BLA/DMF)', 10),
 ('STUDY_REASON','ANNUAL',         'Annual commitment / ongoing stability', 20),
 ('STUDY_REASON','PROCESS_VALIDATION','Process validation / PPQ', 30),
 ('STUDY_REASON','CHANGE_CONTROL', 'Post-approval change (site, process, packaging, supplier)', 40),
 ('STUDY_REASON','DEVELOPMENT',    'Development / R&D', 50),
 ('STUDY_REASON','CLINICAL',       'Clinical supply', 60),
 ('STUDY_REASON','SHELF_LIFE_EXT', 'Shelf-life / retest-period extension', 70),
 ('STUDY_REASON','INVESTIGATION',  'Investigation / complaint', 80),
 ('STUDY_REASON','OTHER',          'Other', 90);

INSERT INTO platform.ref_dictionary_value (dictionary_code, code, label, sort_order, attributes) VALUES
 ('STUDY_LENGTH','6M',  '6 months',  10, '{"months":6}'),
 ('STUDY_LENGTH','12M', '12 months', 20, '{"months":12}'),
 ('STUDY_LENGTH','18M', '18 months', 30, '{"months":18}'),
 ('STUDY_LENGTH','24M', '24 months', 40, '{"months":24}'),
 ('STUDY_LENGTH','36M', '36 months', 50, '{"months":36}'),
 ('STUDY_LENGTH','48M', '48 months', 60, '{"months":48}'),
 ('STUDY_LENGTH','60M', '60 months', 70, '{"months":60}');

INSERT INTO platform.ref_dictionary_value (dictionary_code, code, label, sort_order) VALUES
 ('MEASUREMENT','PCT',       '%', 10), ('MEASUREMENT','PCT_LC', '% of label claim', 20), ('MEASUREMENT','PCT_WW', '% w/w', 30),
 ('MEASUREMENT','MG',        'mg', 40), ('MEASUREMENT','MG_G', 'mg/g', 50), ('MEASUREMENT','MG_ML', 'mg/mL', 60),
 ('MEASUREMENT','PPM',       'ppm', 70), ('MEASUREMENT','PH', 'pH units', 80), ('MEASUREMENT','CFU_G', 'CFU/g', 90),
 ('MEASUREMENT','CFU_ML',    'CFU/mL', 100), ('MEASUREMENT','KP', 'kp (hardness)', 110), ('MEASUREMENT','N', 'N (hardness)', 120),
 ('MEASUREMENT','MIN',       'min', 130), ('MEASUREMENT','SEC', 's', 140), ('MEASUREMENT','MOSM_KG', 'mOsm/kg', 150),
 ('MEASUREMENT','NTU',       'NTU', 160), ('MEASUREMENT','CP', 'cP (viscosity)', 170), ('MEASUREMENT','PARTICLES_CONTAINER', 'particles/container', 180),
 ('MEASUREMENT','AU',        'AU (absorbance)', 190), ('MEASUREMENT','NONE', '(no unit)', 999);

INSERT INTO platform.ref_dictionary_value (dictionary_code, code, label, sort_order, attributes) VALUES
 ('ABBREVIATION','ND',  'Not detected',                   10, '{"qualifier":"ND"}'),
 ('ABBREVIATION','LOQ', 'Below limit of quantitation (<LOQ / BQL)', 20, '{"qualifier":"LOQ"}'),
 ('ABBREVIATION','LOD', 'Below limit of detection (<LOD)', 30, '{"qualifier":"LOD"}'),
 ('ABBREVIATION','NT',  'Not tested',                     40, '{"qualifier":"NT"}'),
 ('ABBREVIATION','NA',  'Not applicable',                 50, '{"qualifier":"NA"}');

INSERT INTO platform.ref_dictionary_value (dictionary_code, code, label, sort_order) VALUES
 ('QUICKLIST','CONFORMS',     'Conforms', 10), ('QUICKLIST','DOES_NOT_CONFORM', 'Does not conform', 20),
 ('QUICKLIST','COMPLIES',     'Complies', 30), ('QUICKLIST','NO_CHANGE', 'No significant change', 40),
 ('QUICKLIST','POSITIVE',     'Positive', 50), ('QUICKLIST','NEGATIVE', 'Negative', 60);

INSERT INTO platform.ref_dictionary_value (dictionary_code, code, label, sort_order) VALUES
 ('DOSAGE_FORM','TABLET','Tablet',10), ('DOSAGE_FORM','FC_TABLET','Film-coated tablet',20), ('DOSAGE_FORM','ER_TABLET','Extended-release tablet',30),
 ('DOSAGE_FORM','CAPSULE','Capsule',40), ('DOSAGE_FORM','ORAL_SOLUTION','Oral solution',50), ('DOSAGE_FORM','ORAL_SUSPENSION','Oral suspension',60),
 ('DOSAGE_FORM','INJECTION_SOLUTION','Solution for injection',70), ('DOSAGE_FORM','LYOPHILISATE','Powder for injection (lyophilised)',80),
 ('DOSAGE_FORM','CREAM','Cream',90), ('DOSAGE_FORM','OINTMENT','Ointment',100), ('DOSAGE_FORM','INHALATION','Inhalation product',110),
 ('DOSAGE_FORM','API_POWDER','Drug substance (powder)',120), ('DOSAGE_FORM','OTHER','Other',999);

-- ---------------------------------------------------------------- packaging
INSERT INTO platform.ref_dictionary_value (dictionary_code, code, label, sort_order) VALUES
 ('CONTAINER','BOTTLE','Bottle',10), ('CONTAINER','VIAL','Vial',20), ('CONTAINER','AMPOULE','Ampoule',30),
 ('CONTAINER','PFS','Prefilled syringe',40), ('CONTAINER','BLISTER_PACK','Blister pack',50), ('CONTAINER','SACHET','Sachet',60),
 ('CONTAINER','TUBE','Tube',70), ('CONTAINER','BAG','Bag / drum liner',80), ('CONTAINER','DRUM','Drum',90), ('CONTAINER','CARTRIDGE','Cartridge',100),
 ('CONTAINER_RESIN','HDPE','HDPE',10), ('CONTAINER_RESIN','LDPE','LDPE',20), ('CONTAINER_RESIN','PP','Polypropylene',30),
 ('CONTAINER_RESIN','PET','PET',40), ('CONTAINER_RESIN','GLASS_I','Glass type I',50), ('CONTAINER_RESIN','GLASS_III','Glass type III',60),
 ('CONTAINER_RESIN','COC','Cyclic olefin copolymer',70), ('CONTAINER_RESIN','ALU','Aluminium',80),
 ('CLOSURE','CRC_PP','Child-resistant PP cap',10), ('CLOSURE','NON_CRC_PP','Non-child-resistant PP cap',20),
 ('CLOSURE','STOPPER_BROMOBUTYL','Bromobutyl rubber stopper',30), ('CLOSURE','STOPPER_CHLOROBUTYL','Chlorobutyl rubber stopper',40),
 ('CLOSURE','FLIP_OFF','Aluminium flip-off seal',50), ('CLOSURE','SCREW_CAP','Screw cap',60),
 ('LINER','INDUCTION','Induction seal liner',10), ('LINER','PULP_FOIL','Pulp/foil liner',20), ('LINER','FOAM','Foam liner',30), ('LINER','NONE','No liner',99),
 ('INNERSEAL','HEAT_INDUCTION','Heat-induction seal',10), ('INNERSEAL','PRESSURE','Pressure-sensitive seal',20), ('INNERSEAL','NONE','No inner seal',99),
 ('DESICCANT','SILICA_CANISTER','Silica gel canister',10), ('DESICCANT','SILICA_SACHET','Silica gel sachet',20),
 ('DESICCANT','MOL_SIEVE','Molecular sieve',30), ('DESICCANT','NONE','No desiccant',99),
 ('FILLER','COTTON','Cotton coil',10), ('FILLER','RAYON','Rayon coil',20), ('FILLER','POLYESTER','Polyester coil',30), ('FILLER','NONE','No filler',99),
 ('BLISTER','PVC','PVC',10), ('BLISTER','PVC_PVDC','PVC/PVDC',20), ('BLISTER','PVC_ACLAR','PVC/PCTFE (Aclar)',30),
 ('BLISTER','ALU_ALU','Alu-Alu (cold form)',40), ('BLISTER','PP','PP',50),
 ('LIDDING_FOIL','ALU_PUSH','Aluminium push-through',10), ('LIDDING_FOIL','ALU_PEEL','Aluminium peel-off',20), ('LIDDING_FOIL','PAPER_ALU','Paper/aluminium (child-resistant)',30),
 ('FORMING_FOIL','PVC_250','PVC 250 µm',10), ('FORMING_FOIL','PVC_PVDC','PVC/PVDC',20), ('FORMING_FOIL','COLD_FORM_ALU','Cold-form aluminium laminate',30),
 ('OTHER_COMPONENT','CARTON','Carton',10), ('OTHER_COMPONENT','LEAFLET','Leaflet',20), ('OTHER_COMPONENT','DROPPER','Dropper',30), ('OTHER_COMPONENT','SPOON','Measuring device',40);

INSERT INTO platform.ref_dictionary_value (dictionary_code, code, label, sort_order) VALUES
 ('CHANGE_REASON','DATA_ENTRY_ERROR','Data entry / transcription error',10),
 ('CHANGE_REASON','REVIEW_COMMENT','Correction from review',20),
 ('CHANGE_REASON','INVESTIGATION','Outcome of investigation',30),
 ('CHANGE_REASON','SCHEDULE_CONFLICT','Schedule conflict / resource availability',40),
 ('CHANGE_REASON','REGULATORY','Regulatory or protocol change',50),
 ('CHANGE_REASON','OTHER','Other (explain)',99);

-- ---------------------------------------------------------- protocol templates
INSERT INTO platform.ref_protocol_template (code, name, study_type, condition_code, timepoint_unit, timepoints, source, note) VALUES
 ('LT_ZONE_II_36M', 'Long-term 25/60, 36 months',      'LONG_TERM',    '25C60RH', 'MONTH', '{0,3,6,9,12,18,24,36}',  'ICH Q1A(R2) §2.2.7: every 3 m year 1, 6 m year 2, annually after', NULL),
 ('LT_ZONE_II_60M', 'Long-term 25/60, 60 months',      'LONG_TERM',    '25C60RH', 'MONTH', '{0,3,6,9,12,18,24,36,48,60}', 'ICH Q1A(R2) §2.2.7', NULL),
 ('LT_ZONE_IVB_36M','Long-term 30/75, 36 months',      'LONG_TERM',    '30C75RH', 'MONTH', '{0,3,6,9,12,18,24,36}',  'ICH Q1A(R2) + WHO TRS 1010 Annex 10', NULL),
 ('INT_30_65_12M',  'Intermediate 30/65, 12 months',   'INTERMEDIATE', '30C65RH', 'MONTH', '{0,6,9,12}',             'ICH Q1A(R2) §2.2.7: min. 4 points incl. initial and final', NULL),
 ('ACC_40_75_6M',   'Accelerated 40/75, 6 months',     'ACCELERATED',  '40C75RH', 'MONTH', '{0,3,6}',                'ICH Q1A(R2) §2.2.7: min. 3 points incl. initial and final', NULL),
 ('ACC_40_75_6M_EXT','Accelerated 40/75, 6 months (extended)', 'ACCELERATED', '40C75RH', 'MONTH', '{0,1,2,3,6}',      'Common industry practice', 'Extra early points for trending'),
 ('REF_LT_36M',     'Refrigerated 5 °C, 36 months',    'LONG_TERM',    '5C',      'MONTH', '{0,3,6,9,12,18,24,36}',  'ICH Q1A(R2) / Q5C', NULL),
 ('REF_ACC_6M',     'Refrigerated product accelerated 25/60, 6 months', 'ACCELERATED', '25C60RH_REF', 'MONTH', '{0,1,3,6}', 'ICH Q5C practice', NULL),
 ('FRZ_LT_36M',     'Frozen −20 °C, 36 months',        'LONG_TERM',    'M20C',    'MONTH', '{0,3,6,9,12,18,24,36}',  'ICH Q1A(R2) / Q5C', NULL),
 ('IN_USE_28D',     'In-use, 28 days',                 'IN_USE',       '25C60RH', 'DAY',   '{0,7,14,21,28}',         'EMA in-use stability note for guidance', NULL);

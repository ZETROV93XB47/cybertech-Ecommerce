-- Extend the five seeded category schemas without making any new attribute mandatory.
-- The initializer contains the corresponding full schemas for fresh databases.
START TRANSACTION;

UPDATE productCategorySchemaTable
SET jsonSchema = JSON_SET(
    jsonSchema,
    '$.properties.screenSizeInches', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.screenResolution', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.screenRefreshRateHz', JSON_OBJECT('type', 'integer', 'minimum', 1),
    '$.properties.panelType', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.weightKg', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.batteryCapacityWh', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.touchscreen', JSON_OBJECT('type', 'boolean'),
    '$.properties.convertible', JSON_OBJECT('type', 'boolean'),
    '$.properties.cpuCores', JSON_OBJECT('type', 'integer', 'minimum', 1),
    '$.properties.storageType', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.wirelessStandard', JSON_OBJECT('type', 'string', 'minLength', 1)
)
WHERE categoryKey = 'COMPUTER';

UPDATE productCategorySchemaTable
SET jsonSchema = JSON_SET(
    jsonSchema,
    '$.properties.screenSizeInches', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.aspectRatio', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.panelType', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.responseTimeMs', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.brightnessNits', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.contrastRatio', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.hdr', JSON_OBJECT('type', 'boolean'),
    '$.properties.curved', JSON_OBJECT('type', 'boolean'),
    '$.properties.adaptiveSync', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.videoInputs', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.weightKg', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.widthMm', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.heightMm', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.depthMm', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0)
)
WHERE categoryKey = 'MONITOR';

UPDATE productCategorySchemaTable
SET jsonSchema = JSON_SET(
    jsonSchema,
    '$.properties.storageType', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.storageInterface', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.powerSupplyW', JSON_OBJECT('type', 'integer', 'minimum', 1),
    '$.properties.videoMemoryGB', JSON_OBJECT('type', 'integer', 'minimum', 1),
    '$.properties.wirelessStandard', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.ethernetController', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.opticalDrive', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.widthMm', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.heightMm', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.depthMm', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.weightKg', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0)
)
WHERE categoryKey = 'DESKTOP';

UPDATE productCategorySchemaTable
SET jsonSchema = JSON_SET(
    jsonSchema,
    '$.properties.screenResolution', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.ramGB', JSON_OBJECT('type', 'integer', 'minimum', 1),
    '$.properties.batteryCapacityMah', JSON_OBJECT('type', 'integer', 'minimum', 1),
    '$.properties.weightG', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.heightMm', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.widthMm', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.thicknessMm', JSON_OBJECT('type', 'number', 'exclusiveMinimum', 0),
    '$.properties.dualSim', JSON_OBJECT('type', 'boolean'),
    '$.properties.simFormat', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.nfc', JSON_OBJECT('type', 'boolean'),
    '$.properties.fastCharging', JSON_OBJECT('type', 'boolean'),
    '$.properties.wirelessCharging', JSON_OBJECT('type', 'boolean'),
    '$.properties.waterResistant', JSON_OBJECT('type', 'boolean'),
    '$.properties.foldable', JSON_OBJECT('type', 'boolean'),
    '$.properties.coreCount', JSON_OBJECT('type', 'integer', 'minimum', 1)
)
WHERE categoryKey = 'SMARTPHONE';

UPDATE productCategorySchemaTable
SET jsonSchema = JSON_SET(
    jsonSchema,
    '$.properties.touchscreen', JSON_OBJECT('type', 'boolean'),
    '$.properties.colorDisplay', JSON_OBJECT('type', 'boolean'),
    '$.properties.bluetooth', JSON_OBJECT('type', 'boolean'),
    '$.properties.wifi', JSON_OBJECT('type', 'boolean'),
    '$.properties.compatibleIphone', JSON_OBJECT('type', 'boolean'),
    '$.properties.strapSize', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.strapMaterial', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.strapColor', JSON_OBJECT('type', 'string', 'minLength', 1),
    '$.properties.nfc', JSON_OBJECT('type', 'boolean'),
    '$.properties.batteryCapacityMah', JSON_OBJECT('type', 'integer', 'minimum', 1)
)
WHERE categoryKey = 'SMARTWATCH';

COMMIT;

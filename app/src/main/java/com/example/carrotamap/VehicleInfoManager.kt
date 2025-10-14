package com.example.carrotamap

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject

/**
 * 车辆信息管理器
 * 负责管理车辆数据、提供选择界面和本地存储
 */
class VehicleInfoManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VehicleInfoManager"
        private const val PREFS_NAME = "VehicleInfoPrefs"
        private const val KEY_VEHICLE_INFO = "vehicle_info"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 获取所有厂商信息
     */
    fun getAllManufacturers(): List<Manufacturer> {
        return listOf(
            Manufacturer("Chrysler", listOf(
                Model("Chrysler Pacifica 2017-18", "CHRYSLER_PACIFICA_2018"),
                Model("Chrysler Pacifica 2019-20", "CHRYSLER_PACIFICA_2020"),
                Model("Chrysler Pacifica 2021-23", "CHRYSLER_PACIFICA_2020"),
                Model("Chrysler Pacifica Hybrid 2017-18", "CHRYSLER_PACIFICA_2018_HYBRID"),
                Model("Chrysler Pacifica Hybrid 2019-25", "CHRYSLER_PACIFICA_2019_HYBRID"),
                Model("Dodge Durango 2020-21", "DODGE_DURANGO"),
                Model("Ram 1500 2019-24", "RAM_1500_5TH_GEN"),
                Model("Ram 2500 2020-24", "RAM_HD_5TH_GEN")
            )),
            Manufacturer("Ford", listOf(
                Model("Ford Bronco Sport 2021-24", "FORD_BRONCO_SPORT_MK1"),
                Model("Ford Escape 2020-22", "FORD_ESCAPE_MK4"),
                Model("Ford Escape 2023-24", "FORD_ESCAPE_MK4_5"),
                Model("Ford Expedition 2022-24", "FORD_EXPEDITION_MK4"),
                Model("Ford Explorer 2020-24", "FORD_EXPLORER_MK6"),
                Model("Ford F-150 2021-23", "FORD_F_150_MK14"),
                Model("Ford Focus 2018", "FORD_FOCUS_MK4"),
                Model("Ford Kuga 2020-23", "FORD_ESCAPE_MK4"),
                Model("Ford Kuga Hybrid 2024", "FORD_ESCAPE_MK4_5"),
                Model("Ford Kuga Plug-in Hybrid 2024", "FORD_ESCAPE_MK4_5"),
                Model("Ford Maverick 2022", "FORD_MAVERICK_MK1"),
                Model("Ford Maverick 2023-24", "FORD_MAVERICK_MK1"),
                Model("Ford Mustang Mach-E 2021-24", "FORD_MUSTANG_MACH_E_MK1"),
                Model("Ford Ranger 2024", "FORD_RANGER_MK2"),
                Model("Lincoln Aviator 2020-24", "FORD_EXPLORER_MK6")
            )),
            Manufacturer("GM", listOf(
                Model("Buick Baby Enclave 2020-23", "BUICK_BABYENCLAVE"),
                Model("Buick LaCrosse 2017-19", "BUICK_LACROSSE"),
                Model("Buick Regal Essence 2018", "BUICK_REGAL"),
                Model("CT6-2019 Advanced ACC", "CADILLAC_CT6_ACC"),
                Model("Cadillac ATS Premium Performance 2018", "CADILLAC_ATS"),
                Model("Cadillac CT6 No ACC", "CADILLAC_CT6_CC"),
                Model("Cadillac Escalade 2017", "CADILLAC_ESCALADE"),
                Model("Cadillac Escalade ESV 2016", "CADILLAC_ESCALADE_ESV"),
                Model("Cadillac Escalade ESV 2019", "CADILLAC_ESCALADE_ESV_2019"),
                Model("Cadillac XT4 2023", "CADILLAC_XT4"),
                Model("Cadillac XT5 No ACC", "CADILLAC_XT5_CC"),
                Model("Chevrolet Bolt EUV 2022-23", "CHEVROLET_BOLT_EUV"),
                Model("Chevrolet Bolt EUV 2022-23 - No-ACC", "CHEVROLET_BOLT_CC"),
                Model("Chevrolet Bolt EV 2017-23 - No-ACC", "CHEVROLET_BOLT_CC"),
                Model("Chevrolet Bolt EV 2022-23", "CHEVROLET_BOLT_EUV"),
                Model("Chevrolet Equinox 2019-22", "CHEVROLET_EQUINOX"),
                Model("Chevrolet Equinox NO ACC 2019-22", "CHEVROLET_EQUINOX_CC"),
                Model("Chevrolet Malibu No ACC", "CHEVROLET_MALIBU_CC"),
                Model("Chevrolet Malibu Premier 2017", "CHEVROLET_MALIBU"),
                Model("Chevrolet Silverado 1500 2020-21", "CHEVROLET_SILVERADO"),
                Model("Chevrolet Suburban 2016-20", "CHEVROLET_SUBURBAN_CC"),
                Model("Chevrolet Suburban Premier 2016-20", "CHEVROLET_SUBURBAN"),
                Model("Chevrolet TRAX 2024", "CHEVROLET_TRAX"),
                Model("Chevrolet Trailblazer 2021-22", "CHEVROLET_TRAILBLAZER"),
                Model("Chevrolet Trailblazer NO ACC 2021-22", "CHEVROLET_TRAILBLAZER_CC"),
                Model("Chevrolet Traverse 2022-23", "CHEVROLET_TRAVERSE"),
                Model("Chevrolet Volt 2017-18", "CHEVROLET_VOLT"),
                Model("Chevrolet Volt 2019", "CHEVROLET_VOLT_2019"),
                Model("Chevrolet Volt LT 2017-18", "CHEVROLET_VOLT_CC"),
                Model("GMC Acadia 2018", "GMC_ACADIA"),
                Model("GMC Sierra 1500 2020-21", "CHEVROLET_SILVERADO"),
                Model("GMC Yukon 2019-20", "GMC_YUKON"),
                Model("GMC Yukon No ACC", "GMC_YUKON_CC"),
                Model("Holden Astra 2017", "HOLDEN_ASTRA")
            )),
            Manufacturer("Honda", listOf(
                Model("Acura ILX 2016-19", "ACURA_ILX"),
                Model("Acura RDX 2016-18", "ACURA_RDX"),
                Model("Acura RDX 2019-21", "ACURA_RDX_3G"),
                Model("Honda Accord 2018-22", "HONDA_ACCORD"),
                Model("Honda Accord Hybrid 2018-22", "HONDA_ACCORD"),
                Model("Honda CR-V 2015-16", "HONDA_CRV"),
                Model("Honda CR-V 2017-22", "HONDA_CRV_5G"),
                Model("Honda CR-V Hybrid 2017-22", "HONDA_CRV_HYBRID"),
                Model("Honda Civic 2016-18", "HONDA_CIVIC"),
                Model("Honda Civic 2019-21", "HONDA_CIVIC_BOSCH"),
                Model("Honda Civic 2022-24", "HONDA_CIVIC_2022"),
                Model("Honda Civic Hatchback 2022-24", "HONDA_CIVIC_2022"),
                Model("Honda Civic Hatchback Hybrid 2023 (Europe only)", "HONDA_CIVIC_2022"),
                Model("Honda Civic Hatchback Hybrid 2025", "HONDA_CIVIC_2022"),
                Model("Honda Fit 2018-20", "HONDA_FIT"),
                Model("Honda Freed 2020", "HONDA_FREED"),
                Model("Honda HR-V 2019-22", "HONDA_HRV"),
                Model("Honda HR-V 2023", "HONDA_HRV_3G"),
                Model("Honda Insight 2019-22", "HONDA_INSIGHT"),
                Model("Honda Inspire 2018", "HONDA_ACCORD"),
                Model("Honda Odyssey 2018-20", "HONDA_ODYSSEY"),
                Model("Honda Passport 2019-25", "HONDA_PILOT"),
                Model("Honda Pilot 2016-22", "HONDA_PILOT"),
                Model("Honda Ridgeline 2017-25", "HONDA_RIDGELINE"),
                Model("Honda e 2020", "HONDA_E")
            )),
            Manufacturer("Hyundai", listOf(
                Model("Genesis EQ900 2017", "GENESIS_EQ900"),
                Model("Genesis EQ900 LIMOUSINE", "GENESIS_EQ900_L"),
                Model("Genesis G70 2018", "GENESIS_G70"),
                Model("Genesis G70 2019-21", "GENESIS_G70_2020"),
                Model("Genesis G80 (2.5T Advanced Trim, with HDA II) 2024", "GENESIS_G80_2ND_GEN_FL"),
                Model("Genesis G80 2018-19", "GENESIS_G80"),
                Model("Genesis G90 2017-20", "GENESIS_G90"),
                Model("Genesis G90 2019", "GENESIS_G90_2019"),
                Model("Genesis GV60 (Advanced Trim) 2023", "GENESIS_GV60_EV_1ST_GEN"),
                Model("Genesis GV70 (2.5T Trim, without HDA II) 2022-24", "GENESIS_GV70_1ST_GEN"),
                Model("Genesis GV70 EV 2020-2023", "GENESIS_GV70_EV_1ST_GEN"),
                Model("Genesis GV70 Electrified (Australia Only) 2022", "GENESIS_GV70_ELECTRIFIED_1ST_GEN"),
                Model("Genesis GV80 2023", "GENESIS_GV80"),
                Model("Hyundai Azera 2022", "HYUNDAI_AZERA_6TH_GEN"),
                Model("Hyundai Azera 2023-2024", "HYUNDAI_AZERA_7TH_GEN"),
                Model("Hyundai Azera Hybrid 2019", "HYUNDAI_AZERA_HEV_6TH_GEN"),
                Model("Hyundai Casper 2023", "HYUNDAI_CASPER"),
                Model("Hyundai Casper EV 2024", "HYUNDAI_CASPER_EV"),
                Model("Hyundai Custin 2023", "HYUNDAI_CUSTIN_1ST_GEN"),
                Model("Hyundai Elantra 2017-18", "HYUNDAI_ELANTRA"),
                Model("Hyundai Elantra 2021-23", "HYUNDAI_ELANTRA_2021"),
                Model("Hyundai Elantra GT 2017-20", "HYUNDAI_ELANTRA_GT_I30"),
                Model("Hyundai Elantra Hybrid 2021-23", "HYUNDAI_ELANTRA_HEV_2021"),
                Model("Hyundai Genesis 2015-16", "HYUNDAI_GENESIS"),
                Model("Hyundai Grandeur 2018-19", "HYUNDAI_GRANDEUR_IG"),
                Model("Hyundai Grandeur HEV 2018-19", "HYUNDAI_GRANDEUR_IG_HEV"),
                Model("Hyundai IONIQ 5 PE (NE1)", "HYUNDAI_IONIQ_5_PE"),
                Model("Hyundai Ioniq 5 (Southeast Asia and Europe only) 2022-24", "HYUNDAI_IONIQ_5"),
                Model("Hyundai Ioniq 5 N (with HDA II) 2024", "HYUNDAI_IONIQ_5_N"),
                Model("Hyundai Ioniq 6 (with HDA II) 2023-24", "HYUNDAI_IONIQ_6"),
                Model("Hyundai Ioniq 9", "HYUNDAI_IONIQ_9"),
                Model("Hyundai Ioniq Electric 2019", "HYUNDAI_IONIQ_EV_LTD"),
                Model("Hyundai Ioniq Electric 2020", "HYUNDAI_IONIQ_EV_2020"),
                Model("Hyundai Ioniq Hybrid 2017-19", "HYUNDAI_IONIQ"),
                Model("Hyundai Ioniq Hybrid 2020-22", "HYUNDAI_IONIQ_HEV_2022"),
                Model("Hyundai Ioniq Plug-in Hybrid 2019", "HYUNDAI_IONIQ_PHEV_2019"),
                Model("Hyundai Ioniq Plug-in Hybrid 2020-22", "HYUNDAI_IONIQ_PHEV"),
                Model("Hyundai Kona 2020", "HYUNDAI_KONA"),
                Model("Hyundai Kona 2022", "HYUNDAI_KONA_2022"),
                Model("Hyundai Kona Electric (with HDA II, Korea only) 2023", "HYUNDAI_KONA_EV_2ND_GEN"),
                Model("Hyundai Kona Electric 2018-21", "HYUNDAI_KONA_EV"),
                Model("Hyundai Kona Electric 2022-23", "HYUNDAI_KONA_EV_2022"),
                Model("Hyundai Kona Hybrid 2020", "HYUNDAI_KONA_HEV"),
                Model("Hyundai Kona Hybrid 2024", "HYUNDAI_KONA_HEV_2ND_GEN"),
                Model("Hyundai Nexo", "HYUNDAI_NEXO"),
                Model("Hyundai Nexo 2021", "HYUNDAI_NEXO_1ST_GEN"),
                Model("Hyundai Palisade 2020-22", "HYUNDAI_PALISADE"),
                Model("Hyundai Porter II EV 2024", "HYUNDAI_PORTER_II_EV"),
                Model("Hyundai SANTAFE (MX5)", "HYUNDAI_SANTAFE_MX5"),
                Model("Hyundai SANTAFE HYBRID (MX5)", "HYUNDAI_SANTAFE_MX5_HEV"),
                Model("Hyundai Santa Cruz 2022-24", "HYUNDAI_SANTA_CRUZ_1ST_GEN"),
                Model("Hyundai Santa Fe 2019-20", "HYUNDAI_SANTA_FE"),
                Model("Hyundai Santa Fe 2021-23", "HYUNDAI_SANTA_FE_2022"),
                Model("Hyundai Santa Fe Hybrid 2022-23", "HYUNDAI_SANTA_FE_HEV_2022"),
                Model("Hyundai Santa Fe Plug-in Hybrid 2022-23", "HYUNDAI_SANTA_FE_PHEV_2022"),
                Model("Hyundai Sonata 2018-19", "HYUNDAI_SONATA_LF"),
                Model("Hyundai Sonata 2020-23", "HYUNDAI_SONATA"),
                Model("Hyundai Sonata 2024-25", "HYUNDAI_SONATA_2024"),
                Model("Hyundai Sonata Hybrid 2020-23", "HYUNDAI_SONATA_HYBRID"),
                Model("Hyundai Staria 2023", "HYUNDAI_STARIA_4TH_GEN"),
                Model("Hyundai Tucson 2021", "HYUNDAI_TUCSON"),
                Model("Hyundai Tucson 2022", "HYUNDAI_TUCSON_4TH_GEN"),
                Model("Hyundai Veloster 2019-20", "HYUNDAI_VELOSTER"),
                Model("KIA EV3 (SV1)", "KIA_EV3"),
                Model("KIA K5 2024 (DL3)", "KIA_K5_DL3_24"),
                Model("KIA K5 HYBRID 2024 (DL3)", "KIA_K5_DL3_24_HEV"),
                Model("Kia Carnival 2022-24", "KIA_CARNIVAL_4TH_GEN"),
                Model("Kia Ceed 2019-21", "KIA_CEED"),
                Model("Kia EV6 (Southeast Asia only) 2022-24", "KIA_EV6"),
                Model("Kia EV6 PE (CV1)", "KIA_EV6_PE"),
                Model("Kia EV9 (MV)", "KIA_EV9"),
                Model("Kia Forte 2019-21", "KIA_FORTE"),
                Model("Kia K5 2019 & 2016", "KIA_K5"),
                Model("Kia K5 2021-24", "KIA_K5_2021"),
                Model("Kia K5 Hybrid 2017", "KIA_K5_HEV"),
                Model("Kia K5 Hybrid 2020-22", "KIA_K5_HEV_2020"),
                Model("Kia K5 Hybrid 2022", "KIA_K5_HEV_2022"),
                Model("Kia K7 2016-2019", "KIA_K7"),
                Model("Kia K7 Hybrid 2016-2019", "KIA_K7_HEV"),
                Model("Kia K8 Hybrid (with HDA II) 2023", "KIA_K8_HEV_1ST_GEN"),
                Model("Kia K9 2016-2019", "KIA_K9"),
                Model("Kia Mohave 2019", "KIA_MOHAVE"),
                Model("Kia Niro EV 2019", "KIA_NIRO_EV"),
                Model("Kia Niro EV 2023", "KIA_NIRO_EV_2ND_GEN"),
                Model("Kia Niro Hybrid 2018", "KIA_NIRO_PHEV"),
                Model("Kia Niro Hybrid 2021", "KIA_NIRO_HEV_2021"),
                Model("Kia Niro Hybrid 2023", "KIA_NIRO_HEV_2ND_GEN"),
                Model("Kia Niro Plug-in Hybrid 2021", "KIA_NIRO_PHEV_2022"),
                Model("Kia Optima 2017", "KIA_OPTIMA_G4"),
                Model("Kia Optima 2019-20", "KIA_OPTIMA_G4_FL"),
                Model("Kia Optima Hybrid 2017", "KIA_OPTIMA_H"),
                Model("Kia Optima Hybrid 2019", "KIA_OPTIMA_H_G4_FL"),
                Model("Kia Seltos 2021", "KIA_SELTOS"),
                Model("Kia Sorento 2018", "KIA_SORENTO"),
                Model("Kia Sorento 2021-23", "KIA_SORENTO_4TH_GEN"),
                Model("Kia Sorento Hybrid 2021-23", "KIA_SORENTO_HEV_4TH_GEN"),
                Model("Kia Soul EV 2019", "KIA_EV_SK3"),
                Model("Kia Sportage 2023-24", "KIA_SPORTAGE_5TH_GEN"),
                Model("Kia Stinger 2018-20", "KIA_STINGER"),
                Model("Kia Stinger 2022-23", "KIA_STINGER_2022")
            )),
            Manufacturer("Mazda", listOf(
                Model("Mazda 3 2017-18", "MAZDA_3"),
                Model("Mazda 6 2017-20", "MAZDA_6"),
                Model("Mazda CX-5 2017-21", "MAZDA_CX5"),
                Model("Mazda CX-5 2022-25", "MAZDA_CX5_2022"),
                Model("Mazda CX-9 2016-20", "MAZDA_CX9"),
                Model("Mazda CX-9 2021-23", "MAZDA_CX9_2021")
            )),
            Manufacturer("Nissan", listOf(
                Model("Nissan Altima 2019-20", "NISSAN_ALTIMA"),
                Model("Nissan Leaf 2018-23", "NISSAN_LEAF"),
                Model("Nissan Rogue 2018-20", "NISSAN_ROGUE"),
                Model("Nissan X-Trail 2017", "NISSAN_XTRAIL")
            )),
            Manufacturer("Subaru", listOf(
                Model("Subaru Ascent 2019-21", "SUBARU_ASCENT"),
                Model("Subaru Ascent 2023", "SUBARU_ASCENT_2023"),
                Model("Subaru Crosstrek 2018-19", "SUBARU_IMPREZA"),
                Model("Subaru Crosstrek 2020-23", "SUBARU_IMPREZA_2020"),
                Model("Subaru Crosstrek Hybrid 2020", "SUBARU_CROSSTREK_HYBRID"),
                Model("Subaru Forester 2017-18", "SUBARU_FORESTER_PREGLOBAL"),
                Model("Subaru Forester 2019-21", "SUBARU_FORESTER"),
                Model("Subaru Forester 2022-24", "SUBARU_FORESTER_2022"),
                Model("Subaru Forester Hybrid 2020", "SUBARU_FORESTER_HYBRID"),
                Model("Subaru Impreza 2017-19", "SUBARU_IMPREZA"),
                Model("Subaru Impreza 2020-22", "SUBARU_IMPREZA_2020"),
                Model("Subaru Legacy 2015-18", "SUBARU_LEGACY_PREGLOBAL"),
                Model("Subaru Legacy 2020-22", "SUBARU_LEGACY"),
                Model("Subaru Outback 2015-17", "SUBARU_OUTBACK_PREGLOBAL"),
                Model("Subaru Outback 2018-19", "SUBARU_OUTBACK_PREGLOBAL_2018"),
                Model("Subaru Outback 2020-22", "SUBARU_OUTBACK"),
                Model("Subaru Outback 2023", "SUBARU_OUTBACK_2023"),
                Model("Subaru XV 2018-19", "SUBARU_IMPREZA"),
                Model("Subaru XV 2020-21", "SUBARU_IMPREZA_2020")
            )),
            Manufacturer("Toyota", listOf(
                Model("Lexus ES 2019-24", "LEXUS_ES_TSS2"),
                Model("Lexus ES Hybrid 2019-25", "LEXUS_ES_TSS2"),
                Model("Lexus IS 2022-23", "LEXUS_IS_TSS2"),
                Model("Lexus LC 2024", "LEXUS_LC_TSS2"),
                Model("Lexus NX 2020-21", "LEXUS_NX_TSS2"),
                Model("Lexus NX Hybrid 2020-21", "LEXUS_NX_TSS2"),
                Model("Lexus RX 2020-22", "LEXUS_RX_TSS2"),
                Model("Lexus RX Hybrid 2020-22", "LEXUS_RX_TSS2"),
                Model("Lexus UX Hybrid 2019-24", "TOYOTA_COROLLA_TSS2"),
                Model("Toyota Alphard 2019-20", "TOYOTA_ALPHARD_TSS2"),
                Model("Toyota Alphard Hybrid 2021", "TOYOTA_ALPHARD_TSS2"),
                Model("Toyota C-HR 2021", "TOYOTA_CHR_TSS2"),
                Model("Toyota C-HR Hybrid 2021-22", "TOYOTA_CHR_TSS2"),
                Model("Toyota Corolla 2020-22", "TOYOTA_COROLLA_TSS2"),
                Model("Toyota Corolla Cross (Non-US only) 2020-23", "TOYOTA_COROLLA_TSS2"),
                Model("Toyota Corolla Cross Hybrid (Non-US only) 2020-22", "TOYOTA_COROLLA_TSS2"),
                Model("Toyota Corolla Hatchback 2019-22", "TOYOTA_COROLLA_TSS2"),
                Model("Toyota Corolla Hybrid (South America only) 2020-23", "TOYOTA_COROLLA_TSS2"),
                Model("Toyota Corolla Hybrid 2020-22", "TOYOTA_COROLLA_TSS2"),
                Model("Toyota Highlander 2020-23", "TOYOTA_HIGHLANDER_TSS2"),
                Model("Toyota Highlander Hybrid 2020-23", "TOYOTA_HIGHLANDER_TSS2"),
                Model("Toyota Prius 2021-22", "TOYOTA_PRIUS_TSS2"),
                Model("Toyota Prius Prime 2021-22", "TOYOTA_PRIUS_TSS2"),
                Model("Toyota RAV4 2019-21", "TOYOTA_RAV4_TSS2"),
                Model("Toyota RAV4 2022", "TOYOTA_RAV4_TSS2_2022"),
                Model("Toyota RAV4 2023-25", "TOYOTA_RAV4_TSS2_2023"),
                Model("Toyota RAV4 Hybrid 2019-21", "TOYOTA_RAV4_TSS2"),
                Model("Toyota RAV4 Hybrid 2022", "TOYOTA_RAV4_TSS2_2022"),
                Model("Toyota RAV4 Hybrid 2023-25", "TOYOTA_RAV4_TSS2_2023"),
                Model("Toyota RAV4 Prime 2021-23", "TOYOTA_RAV4_PRIME"),
                Model("Toyota Sienna 2021-23", "TOYOTA_SIENNA_4TH_GEN"),
                Model("Toyota Yaris 2023 (Non-US only)", "TOYOTA_YARIS")
            )),
            Manufacturer("Volkswagen", listOf(
                Model("Audi A3 2014-19", "AUDI_A3_MK3"),
                Model("Audi A3 Sportback e-tron 2017-18", "AUDI_A3_MK3"),
                Model("Audi Q2 2018", "AUDI_Q2_MK1"),
                Model("Audi Q3 2019-23", "AUDI_Q3_MK2"),
                Model("Audi RS3 2018", "AUDI_A3_MK3"),
                Model("Audi S3 2015-17", "AUDI_A3_MK3"),
                Model("CUPRA Ateca 2018-23", "SEAT_ATECA_MK1"),
                Model("MAN TGE 2017-24", "VOLKSWAGEN_CRAFTER_MK2"),
                Model("MAN eTGE 2020-24", "VOLKSWAGEN_CRAFTER_MK2"),
                Model("SEAT Alhambra 2018-20", "VOLKSWAGEN_SHARAN_MK2"),
                Model("SEAT Ateca 2016-23", "SEAT_ATECA_MK1"),
                Model("SEAT Leon 2014-20", "SEAT_ATECA_MK1"),
                Model("Volkswagen Arteon 2018-23", "VOLKSWAGEN_ARTEON_MK1"),
                Model("Volkswagen Arteon R 2020-23", "VOLKSWAGEN_ARTEON_MK1"),
                Model("Volkswagen Arteon Shooting Brake 2020-23", "VOLKSWAGEN_ARTEON_MK1"),
                Model("Volkswagen Arteon eHybrid 2020-23", "VOLKSWAGEN_ARTEON_MK1"),
                Model("Volkswagen Atlas 2018-23", "VOLKSWAGEN_ATLAS_MK1"),
                Model("Volkswagen Atlas Cross Sport 2020-22", "VOLKSWAGEN_ATLAS_MK1"),
                Model("Volkswagen CC 2018-22", "VOLKSWAGEN_ARTEON_MK1"),
                Model("Volkswagen Caddy 2019", "VOLKSWAGEN_CADDY_MK3"),
                Model("Volkswagen Caddy Maxi 2019", "VOLKSWAGEN_CADDY_MK3"),
                Model("Volkswagen California 2021-23", "VOLKSWAGEN_TRANSPORTER_T61"),
                Model("Volkswagen Caravelle 2020", "VOLKSWAGEN_TRANSPORTER_T61"),
                Model("Volkswagen Crafter 2017-24", "VOLKSWAGEN_CRAFTER_MK2"),
                Model("Volkswagen Golf 2015-20", "VOLKSWAGEN_GOLF_MK7"),
                Model("Volkswagen Golf Alltrack 2015-19", "VOLKSWAGEN_GOLF_MK7"),
                Model("Volkswagen Golf GTD 2015-20", "VOLKSWAGEN_GOLF_MK7"),
                Model("Volkswagen Golf GTE 2015-20", "VOLKSWAGEN_GOLF_MK7"),
                Model("Volkswagen Golf GTI 2015-21", "VOLKSWAGEN_GOLF_MK7"),
                Model("Volkswagen Golf R 2015-19", "VOLKSWAGEN_GOLF_MK7"),
                Model("Volkswagen Golf SportsVan 2015-20", "VOLKSWAGEN_GOLF_MK7"),
                Model("Volkswagen Grand California 2019-24", "VOLKSWAGEN_CRAFTER_MK2"),
                Model("Volkswagen Jetta 2015-18", "VOLKSWAGEN_JETTA_MK6"),
                Model("Volkswagen Jetta 2018-23", "VOLKSWAGEN_JETTA_MK7"),
                Model("Volkswagen Jetta GLI 2021-23", "VOLKSWAGEN_JETTA_MK7"),
                Model("Volkswagen Passat 2015-22", "VOLKSWAGEN_PASSAT_MK8"),
                Model("Volkswagen Passat NMS 2017-22", "VOLKSWAGEN_PASSAT_NMS"),
                Model("Volkswagen Polo 2018-23", "VOLKSWAGEN_POLO_MK6"),
                Model("Volkswagen Sharan 2018-22", "VOLKSWAGEN_SHARAN_MK2"),
                Model("Volkswagen T-Cross 2021", "VOLKSWAGEN_TCROSS_MK1"),
                Model("Volkswagen T-Roc 2018-23", "VOLKSWAGEN_TROC_MK1"),
                Model("Volkswagen Taos 2022-23", "VOLKSWAGEN_TAOS_MK1"),
                Model("Volkswagen Teramont 2018-22", "VOLKSWAGEN_ATLAS_MK1"),
                Model("Volkswagen Teramont Cross Sport 2021-22", "VOLKSWAGEN_ATLAS_MK1"),
                Model("Volkswagen Teramont X 2021-22", "VOLKSWAGEN_ATLAS_MK1"),
                Model("Volkswagen Tiguan 2018-23", "VOLKSWAGEN_TIGUAN_MK2"),
                Model("Volkswagen Tiguan eHybrid 2021-23", "VOLKSWAGEN_TIGUAN_MK2"),
                Model("Volkswagen Touran 2016-23", "VOLKSWAGEN_TOURAN_MK2"),
                Model("Volkswagen e-Crafter 2018-24", "VOLKSWAGEN_CRAFTER_MK2"),
                Model("Volkswagen e-Golf 2014-20", "VOLKSWAGEN_GOLF_MK7"),
                Model("Škoda Fabia 2022-23", "SKODA_FABIA_MK4"),
                Model("Škoda Kamiq 2021-23", "SKODA_KAMIQ_MK1"),
                Model("Škoda Karoq 2019-23", "SKODA_KAROQ_MK1"),
                Model("Škoda Kodiaq 2017-23", "SKODA_KODIAQ_MK1"),
                Model("Škoda Octavia 2015-19", "SKODA_OCTAVIA_MK3"),
                Model("Škoda Octavia RS 2016", "SKODA_OCTAVIA_MK3"),
                Model("Škoda Octavia Scout 2017-19", "SKODA_OCTAVIA_MK3"),
                Model("Škoda Superb 2015-22", "SKODA_SUPERB_MK3")
            )),
            Manufacturer("比亚迪", listOf(
                Model("BYD ATTO 3", "BYD_ATTO_3"),
                Model("BYD DENZA D9 DMI", "BYD_DENZA_D9_DMI"),
                Model("BYD DOLPHIN EV", "BYD_DOLPHIN_EV"),
                Model("BYD HAN DM 20-21", "BYD_HAN_DM_20_21"),
                Model("BYD FORWARD MOCK", "BYD_FORWARD_MOCK"),
                Model("BYD HAN DMI 22", "BYD_HAN_DMI_22"),
                Model("BYD HAN EV 20-21", "BYD_HAN_EV_20_21"),
                Model("BYD HAN EV 22", "BYD_HAN_EV_22"),
                Model("BYD HAN EV 25", "BYD_HAN_EV_25"),
                Model("BYD HWJ 07 DMI", "BYD_HWJ_07_DMI"),
                Model("BYD QIN L DMI", "BYD_QIN_L_DMI"),
                Model("BYD QIN PLUS DMI", "BYD_QIN_PLUS_DMI"),
                Model("BYD QZJ 05 DMI", "BYD_QZJ_05_DMI"),
                Model("BYD QIN PRO DM", "BYD_QIN_PRO_DM"),
                Model("BYD SEAL 06 DMI", "BYD_SEAL_06_DMI"),
                Model("BYD SEAL 07 DMI 22", "BYD_SEAL_07_DMI_22"),
                Model("BYD SEAL 07 DMI 24", "BYD_SEAL_07_DMI_24"),
                Model("BYD SONG L DMI", "BYD_SONG_L_DMI"),
                Model("BYD SONG PLUS 5G DMI 22", "BYD_SONG_PLUS_5G_DMI_22"),
                Model("BYD SONG PLUS DMI 21", "BYD_SONG_PLUS_DMI_21"),
                Model("BYD SONG PLUS DMI 22", "BYD_SONG_PLUS_DMI_22"),
                Model("BYD SONG PLUS DMI 23", "BYD_SONG_PLUS_DMI_23"),
                Model("BYD SONG PLUS DMI 23 ALT", "BYD_SONG_PLUS_DMI_23_ALT"),
                Model("BYD SONG PLUS EV 21-22", "BYD_SONG_PLUS_EV_21_22"),
                Model("BYD SONG PRO DMI 22", "BYD_SONG_PRO_DMI_22"),
                Model("BYD SONG PRO DMI 23", "BYD_SONG_PRO_DMI_23"),
                Model("BYD TANG DM", "BYD_TANG_DM"),
                Model("BYD TANG DM ALT", "BYD_TANG_DM_ALT"),
                Model("BYD TANG DMI 21", "BYD_TANG_DMI_21"),
                Model("BYD TANG DMI 21 ALT", "BYD_TANG_DMI_21_ALT"),
                Model("BYD TANG DMI 23", "BYD_TANG_DMI_23"),
                Model("BYD TANG DMI 24", "BYD_TANG_DMI_24"),
                Model("BYD TANG DMP 22", "BYD_TANG_DMP_22"),
                Model("BYD TANG DMI 24 ALT", "BYD_TANG_DMI_24_ALT"),
                Model("BYD TANG GAS", "BYD_TANG_GAS"),
                Model("BYD YUAN PLUS DMI 22", "BYD_YUAN_PLUS_DMI_22"),
                Model("BYD YUAN PLUS EV 22", "BYD_YUAN_PLUS_EV_22"),
                Model("BYD YUAN UP EV", "BYD_YUAN_UP_EV")
            )),
            Manufacturer("特斯拉", listOf(
                Model("Tesla Model 3 2017-24", "TESLA_MODEL_3"),
                Model("Tesla Model Y 2020-24", "TESLA_MODEL_Y")
            )),
            Manufacturer("吉利", listOf(
                Model("吉利缤越 2018-24", "GEELY_BINYUE"),
                Model("吉利帝豪 2016-24", "GEELY_DIHAO")
            )),
            Manufacturer("长安", listOf(
                Model("长安欧尚 2018-24", "CHANGAN_OUSHANG")
            )),
            Manufacturer("其他", listOf(
                Model("不在清单中", "OTHER_NOT_IN_LIST")
            ))
        )
    }
    
    /**
     * 根据厂商名称获取车型列表
     */
    fun getModelsByManufacturer(manufacturerName: String): List<Model> {
        return getAllManufacturers().find { it.name == manufacturerName }?.models ?: emptyList()
    }
    
    /**
     * 保存车辆信息到本地存储
     */
    fun saveVehicleInfo(vehicleInfo: VehicleInfo): Boolean {
        return try {
            val json = JSONObject().apply {
                put("manufacturer", vehicleInfo.manufacturer)
                put("model", vehicleInfo.model)
                put("fingerprint", vehicleInfo.fingerprint)
                put("timestamp", System.currentTimeMillis())
            }
            
            prefs.edit().putString(KEY_VEHICLE_INFO, json.toString()).apply()
            Log.i(TAG, "✅ 车辆信息保存成功: ${vehicleInfo.manufacturer} ${vehicleInfo.model}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存车辆信息失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 从本地存储获取车辆信息
     */
    fun getVehicleInfo(): VehicleInfo? {
        return try {
            val jsonStr = prefs.getString(KEY_VEHICLE_INFO, null)
            if (jsonStr != null) {
                val json = JSONObject(jsonStr)
                VehicleInfo(
                    manufacturer = json.getString("manufacturer"),
                    model = json.getString("model"),
                    fingerprint = json.getString("fingerprint")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取车辆信息失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 清除本地存储的车辆信息
     */
    fun clearVehicleInfo() {
        prefs.edit().remove(KEY_VEHICLE_INFO).apply()
        Log.i(TAG, "🗑️ 车辆信息已清除")
    }
}

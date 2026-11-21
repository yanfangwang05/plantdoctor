package cn.caas.plantdoctor.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routing table is part of the published description of the system, so it is
 * asserted directly rather than only through the HTTP layer.
 */
class InsectCropTypeTest {

    @Test
    void exposesTenSelectableCategories() {
        List<InsectCropType> selectable = InsectCropType.selectable();
        assertEquals(10, selectable.size());
        assertFalse(selectable.contains(InsectCropType.UNSUPPORTED));
    }

    @Test
    void sendsCornAndSoybeanToTheDedicatedPath() {
        assertTrue(InsectCropType.CORN.isDedicatedPath());
        assertTrue(InsectCropType.SOYBEAN.isDedicatedPath());
        assertEquals("14", InsectCropType.CORN.getDedicatedImageType());
        assertEquals("15", InsectCropType.SOYBEAN.getDedicatedImageType());
        assertNull(InsectCropType.CORN.getVendorCropId());
    }

    @Test
    void sendsTheOtherEightCategoriesToTheGeneralPath() {
        for (InsectCropType type : InsectCropType.selectable()) {
            if (type.isDedicatedPath()) {
                continue;
            }
            assertNull(type.getDedicatedImageType(), type + " should not have an imageType");
            assertTrue(type.getVendorCropId() != null && !type.getVendorCropId().isBlank(),
                    type + " needs a crop_id");
        }
        assertEquals("123", InsectCropType.APPLE.getVendorCropId());
        assertEquals("1", InsectCropType.CITRUS.getVendorCropId());
        assertEquals("127", InsectCropType.GRAPE.getVendorCropId());
        assertEquals("132", InsectCropType.RICE.getVendorCropId());
        assertEquals("131", InsectCropType.WHEAT.getVendorCropId());
        assertEquals("143", InsectCropType.INSECT.getVendorCropId());
        assertEquals("142", InsectCropType.YEE.getVendorCropId());
        assertEquals("2", InsectCropType.GRASS.getVendorCropId());
    }

    @Test
    void resolvesByCodeKeyAndDisplayName() {
        assertSame(InsectCropType.WHEAT, InsectCropType.resolve("5"));
        assertSame(InsectCropType.WHEAT, InsectCropType.resolve("WHEAT"));
        assertSame(InsectCropType.WHEAT, InsectCropType.resolve("wheat"));
        assertSame(InsectCropType.WHEAT, InsectCropType.resolve("Wheat"));
    }

    @Test
    void rejectsUnresolvableInput() {
        assertSame(InsectCropType.UNSUPPORTED, InsectCropType.resolve(null));
        assertSame(InsectCropType.UNSUPPORTED, InsectCropType.resolve(""));
        assertSame(InsectCropType.UNSUPPORTED, InsectCropType.resolve("   "));
        assertSame(InsectCropType.UNSUPPORTED, InsectCropType.resolve("cassava"));
        assertSame(InsectCropType.UNSUPPORTED, InsectCropType.resolve("99"));
        assertSame(InsectCropType.UNSUPPORTED, InsectCropType.codeOf(0));
    }
}

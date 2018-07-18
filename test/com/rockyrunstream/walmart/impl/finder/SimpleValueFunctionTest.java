package com.rockyrunstream.walmart.impl.finder;

import org.junit.Assert;
import org.junit.Test;

public class SimpleValueFunctionTest {

    @Test
    public void valueTest() {
        final double[][] values = new double[5][5];
        final SimpleValueFunction function = new SimpleValueFunction(values);
        Assert.assertEquals(function.value(0, 0), 0D, 0D);

        Assert.assertEquals(function.value(3, 3), 0D, 0D);
        values[3][3] = 5D;
        Assert.assertEquals(function.value(3, 3), 5D, 0D);
    }

    @Test(expected = RuntimeException.class)
    public void notConfiguredTest() {
        new SimpleValueFunction(null);
    }
}

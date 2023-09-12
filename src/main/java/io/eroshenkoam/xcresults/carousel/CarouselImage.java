package io.eroshenkoam.xcresults.carousel;

import java.io.Serializable;

public class CarouselImage implements Serializable {

    private final String name;
    private final String base64;

    public CarouselImage(final String name, final String base64) {
        this.name = name;
        this.base64 = base64;
    }

    public String getName() {
        return name;
    }

    public String getBase64() {
        return base64;
    }

}

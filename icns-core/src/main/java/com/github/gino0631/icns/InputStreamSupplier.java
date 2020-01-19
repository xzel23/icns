package com.github.gino0631.icns;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
interface InputStreamSupplier {
    InputStream newInputStream() throws IOException;
}

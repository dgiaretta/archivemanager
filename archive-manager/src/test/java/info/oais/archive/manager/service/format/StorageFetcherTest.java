package info.oais.archive.manager.service.format;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link StorageFetcher#direct}: file-sharing services' links to their pages become links to the files. */
class StorageFetcherTest {

    @Test
    void turnsSharingPagesIntoDownloads() {
        assertThat(StorageFetcher.direct(URI.create("https://www.dropbox.com/scl/fi/vv8avkbj4zt9o5fxe4clx/"
                + "T_pl_20031101_v01_moments3s.dat?rlkey=xem6bu2aeqj74n4hn0h28ois1&st=0asp6ryr&dl=0")))
                .hasToString("https://www.dropbox.com/scl/fi/vv8avkbj4zt9o5fxe4clx/T_pl_20031101_v01_moments3s.dat"
                        + "?rlkey=xem6bu2aeqj74n4hn0h28ois1&st=0asp6ryr&dl=1");
        assertThat(StorageFetcher.direct(URI.create("https://www.dropbox.com/s/abc/data.bin")))
                .hasToString("https://www.dropbox.com/s/abc/data.bin?dl=1");
        assertThat(StorageFetcher.direct(URI.create("https://www.dropbox.com/s/abc/data.bin?raw=1&dl=0")))
                .hasToString("https://www.dropbox.com/s/abc/data.bin?dl=1");
        assertThat(StorageFetcher.direct(URI.create("https://drive.google.com/file/d/1AbC-dE/view?usp=sharing")))
                .hasToString("https://drive.google.com/uc?export=download&id=1AbC-dE");
        assertThat(StorageFetcher.direct(URI.create("https://github.com/owner/repo/blob/main/data/x.dat")))
                .hasToString("https://raw.githubusercontent.com/owner/repo/main/data/x.dat");
        URI plain = URI.create("https://example.org/data.bin?dl=0");
        assertThat(StorageFetcher.direct(plain)).isSameAs(plain);
        URI home = URI.create("https://www.dropbox.com/home");
        assertThat(StorageFetcher.direct(home)).isSameAs(home);
    }
}

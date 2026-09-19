package info.oais.archive.manager.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * A brief getting-started tutorial plus a link out to the generated Javadoc
 * site (bundled at {@code /apidocs/index.html}, see the {@code pom.xml}
 * comment on {@code maven-javadoc-plugin} for how it gets there). Open, no
 * login needed -- documentation, not archive data, same reasoning as
 * {@link DownloadController}.
 */
@Controller
public class HelpController {

    @GetMapping("/help")
    public String help() {
        return "help";
    }
}

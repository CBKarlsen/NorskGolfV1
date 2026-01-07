package fritids.norskgolf;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaRedirectController {
    // This tells Spring: "If you don't recognize the URL (like /overview),
    // just send the user to index.html and let React handle it."
    @RequestMapping(value = "/{path:[^\\.]*}")
    public String redirect() {
        return "forward:/index.html";
    }
}
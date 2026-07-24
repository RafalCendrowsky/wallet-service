package me.rcendrow.wallet.api

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/")
class HomeController {

    @GetMapping()
    fun home(): String {
        return "redirect:/swagger-ui/index.html"
    }
}

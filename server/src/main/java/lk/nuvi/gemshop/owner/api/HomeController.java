package lk.nuvi.gemshop.owner.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.owner.Home;
import lk.nuvi.gemshop.owner.api.HomeDtos.HomeFigures;
import lk.nuvi.gemshop.owner.api.HomeDtos.PhoneView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Home page figures per role, and the owner's phone view (P4-01). */
@RestController
@RequestMapping("/api/home")
@Tag(name = "Home")
public class HomeController {

    private final Home home;

    public HomeController(Home home) {
        this.home = home;
    }

    @GetMapping
    @PreAuthorize(Roles.ANY)
    @Operation(operationId = "getHome", summary = "Home figures for the signed-in person and the branch at the top")
    public HomeFigures home() {
        return home.home();
    }

    @GetMapping("/phone")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "getPhoneView", summary = "Owner phone view: today's sales, cash, stock, approvals, sold gems, alerts")
    public PhoneView phone(@Parameter(description = "Branch code, or \"all\"") @RequestParam(required = false) String branch) {
        return home.phone(branch);
    }
}

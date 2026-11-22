package lk.nuvi.gemshop.audit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lk.nuvi.gemshop.audit.api.AuditDtos.LogLine;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.paging.PageResult;
import lk.nuvi.gemshop.files.Downloads;
import lk.nuvi.gemshop.files.excel.ExcelExport;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Activity log (prototype k-owner-control/activity-log.html, K4). Read only: there is no API to change or delete a line.
 */
@RestController
@RequestMapping("/api/activity-log")
@Tag(name = "Activity log")
public class ActivityLogController {

    private final ActivityLog log;
    private final CurrentUser current;

    private static final int EXPORT_MAX = 5000;
    private static final ZoneId SHOP = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DMY_HM = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PdfDocuments pdfs;
    private final ExcelExport excel;
    private final BranchRepository branches;

    public ActivityLogController(ActivityLog log, CurrentUser current, PdfDocuments pdfs, ExcelExport excel,
            BranchRepository branches) {
        this.log = log;
        this.current = current;
        this.pdfs = pdfs;
        this.excel = excel;
        this.branches = branches;
    }

    @PreAuthorize(Roles.OWNER)
    @GetMapping
    @Operation(operationId = "getActivityLog", summary = "Who did what and when, newest first (last 30 days unless dates are given)")
    public PageResult<LogLine> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "A person's id, or \"system\" for automatic entries")
            @RequestParam(required = false) String user,
            @Parameter(description = "Text in the action or detail") @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return log.search(current.scope(), filter(from, to, user, q), Math.max(0, page), PageResult.size(size, 50, 300));
    }

    /** Export button: the same search as a PDF (shop header) or an Excel file, at most 5,000 lines. */
    @PreAuthorize(Roles.OWNER)
    @GetMapping("/export")
    @Operation(operationId = "exportActivityLog", summary = "The activity log as PDF or Excel (same filters as the list)")
    public ResponseEntity<byte[]> export(
            @Parameter(description = "pdf or xlsx") @RequestParam String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String q) {
        ActivityLog.Filter f = filter(from, to, user, q);
        List<LogLine> rows = log.search(current.scope(), f, 0, EXPORT_MAX).items();
        String name = Downloads.dated("Activity log");
        if ("xlsx".equalsIgnoreCase(format)) {
            return Downloads.xlsx(excel.write("Activity log", List.of(
                    ExcelExport.Column.<LogLine>dateTime("When", 18, LogLine::at),
                    ExcelExport.Column.<LogLine>text("Who", 24, LogLine::userName),
                    ExcelExport.Column.<LogLine>text("Action", 24, LogLine::action),
                    ExcelExport.Column.<LogLine>text("Detail", 70, LogLine::detail),
                    ExcelExport.Column.<LogLine>text("Branch", 16, l -> branchName(l.branch()))), rows), name);
        }
        if (!"pdf".equalsIgnoreCase(format)) {
            throw ApiException.badRequest("BAD_FORMAT", "Format must be pdf or xlsx.");
        }
        LocalDate shown = f.to() == null ? LocalDate.now(SHOP) : f.to();
        String period = DMY.format(f.from() == null ? shown.minusDays(30) : f.from()) + " – " + DMY.format(shown);
        byte[] pdf = pdfs.render(new PdfDocuments.Spec("ACTIVITY LOG", List.of(new PdfDocuments.Line("Period", period),
                        new PdfDocuments.Line("Entries", String.valueOf(rows.size())),
                        new PdfDocuments.Line("Printed", DMY_HM.format(LocalDateTime.now(SHOP)))),
                        "This log cannot be edited or deleted. Every action is recorded automatically by the system.", true, null),
                body -> {
                    var t = body.table(new float[] {14, 16, 16, 42, 12}, "When", "Who", "Action", "Detail", "Branch");
                    for (LogLine l : rows) {
                        t.row(DMY_HM.format(LocalDateTime.ofInstant(l.at(), SHOP)), l.userName(), l.action(), l.detail(),
                                branchName(l.branch()));
                    }
                    t.end();
                });
        return Downloads.pdf(pdf, name, true);
    }

    private String branchName(String code) {
        if (CurrentUser.ALL.equals(code)) {
            return "All branches";
        }
        return branches.findByCode(code).map(b -> b.getDisplayName()).orElse(code);
    }

    private static ActivityLog.Filter filter(LocalDate from, LocalDate to, String user, String q) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("BAD_DATES", "'From' must be on or before 'To'.");
        }
        boolean system = "system".equalsIgnoreCase(user);
        Long userId = null;
        if (!system && user != null && !user.isBlank()) {
            try {
                userId = Long.parseLong(user);
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("UNKNOWN_USER", "Unknown user: " + user);
            }
        }
        return new ActivityLog.Filter(from, to, userId, system, q);
    }
}

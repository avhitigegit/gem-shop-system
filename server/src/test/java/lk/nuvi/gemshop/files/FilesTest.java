package lk.nuvi.gemshop.files;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.imageio.ImageIO;
import lk.nuvi.gemshop.SignIn;
import lk.nuvi.gemshop.SignIn.Signed;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.common.crypto.FieldCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P1-05: uploads (type and size check), resizing to about 300 KB with a thumbnail, private links. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FilesTest.TestApi.class})
class FilesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired FileStorage storage;
    @Autowired FileService files;
    @Autowired FieldCrypto crypto;

    private final Map<String, Signed> who = new LinkedHashMap<>();

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ?",
                encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES, STAFF, ACCOUNTANT}) {
            who.put(e, SignIn.login(mvc, e));
        }
    }

    // ---------------------------------------------------------------- photos

    @Test
    void bigPhonePhotoIsMadeSmallWithAThumbnail() throws Exception {
        byte[] photo = noisyJpeg(4000, 3000);
        assertThat(photo.length).isGreaterThan(2 * 1024 * 1024);
        JsonNode f = upload(STAFF, "PRODUCT_PHOTO", "IMG_0042.JPG", photo, 201);
        assertThat(f.path("sizeBytes").asLong()).isLessThanOrEqualTo(ImageProcessor.TARGET_BYTES);
        assertThat(f.path("contentType").asString()).isEqualTo("image/jpeg");
        assertThat(Math.max(f.path("width").asInt(), f.path("height").asInt())).isLessThanOrEqualTo(1600);
        assertThat(f.path("name").asString()).isEqualTo("IMG_0042.jpg");
        assertThat(f.path("attached").asBoolean()).isFalse();

        // the links work without login (for <img src>) …
        MvcResult main = mvc.perform(get(f.path("url").asString())).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
        assertThat(main.getResponse().getContentAsByteArray()).hasSize(f.path("sizeBytes").asInt());
        byte[] thumb = mvc.perform(get(f.path("thumbUrl").asString())).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray();
        BufferedImage t = ImageIO.read(new ByteArrayInputStream(thumb));
        assertThat(Math.max(t.getWidth(), t.getHeight())).isLessThanOrEqualTo(ImageProcessor.THUMB_SIDE);
        // … and the uploader's branch is kept
        assertThat(jdbc.queryForObject("select b.code from stored_file f join branch b on b.id = f.branch_id where f.public_id = ?",
                String.class, UUID.fromString(f.path("id").asString()))).isEqualTo("ella");
    }

    @Test
    void smallPhotoIsNotMadeBiggerAndSeeThroughBecomesWhite() throws Exception {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB); // fully see-through
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        JsonNode f = upload(OWNER, "PRODUCT_PHOTO", "gem.png", png.toByteArray(), 201);
        assertThat(f.path("width").asInt()).isEqualTo(400);
        assertThat(f.path("contentType").asString()).isEqualTo("image/jpeg");
        byte[] stored = mvc.perform(get(f.path("url").asString())).andReturn().getResponse().getContentAsByteArray();
        assertThat(new Color(ImageIO.read(new ByteArrayInputStream(stored)).getRGB(10, 10))).isEqualTo(Color.WHITE);
        // the logo keeps PNG (see-through background on invoices)
        JsonNode logo = upload(OWNER, "LOGO", "logo.png", png.toByteArray(), 201);
        assertThat(logo.path("contentType").asString()).isEqualTo("image/png");
    }

    // ---------------------------------------------------------------- type checks

    @Test
    void pdfOnlyWhereAllowed() throws Exception {
        byte[] pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        JsonNode cert = upload(OWNER, "CERTIFICATE", "GRS-2024-001.pdf", pdf, 201);
        assertThat(cert.path("contentType").asString()).isEqualTo("application/pdf");
        assertThat(cert.path("thumbUrl").isNull() || cert.path("thumbUrl").isMissingNode()).isTrue();
        mvc.perform(get(cert.path("url").asString())).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
        upload(ACCOUNTANT, "BILL_PHOTO", "bill.pdf", pdf, 201);
        JsonNode err = upload(OWNER, "PRODUCT_PHOTO", "gem.pdf", pdf, 400);
        assertThat(err.path("code").asString()).isEqualTo("FILE_TYPE");
        assertThat(err.path("message").asString()).isEqualTo("Please choose a photo (JPEG, PNG or WebP).");
    }

    @Test
    void renamedFilesAreRefused() throws Exception {
        // a web page renamed to .jpg, with an image content type
        JsonNode err = upload(OWNER, "PRODUCT_PHOTO", "photo.jpg", "<html><script>alert(1)</script></html>".getBytes(), 400);
        assertThat(err.path("code").asString()).isEqualTo("FILE_TYPE");
        // iPhone HEIC: says what to do
        byte[] heic = new byte[64];
        System.arraycopy(new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'}, 0, heic, 0, 12);
        assertThat(upload(OWNER, "PRODUCT_PHOTO", "IMG_1.HEIC", heic, 400).path("message").asString()).contains("HEIC");
        // a "JPEG" that is broken inside
        byte[] broken = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        assertThat(upload(OWNER, "PRODUCT_PHOTO", "x.jpg", broken, 400).path("code").asString()).isEqualTo("FILE_BROKEN");
        assertThat(upload(OWNER, "PRODUCT_PHOTO", "x.jpg", new byte[0], 400).path("code").asString()).isEqualTo("FILE_EMPTY");
    }

    @Test
    void uploadNeedsLogin() throws Exception {
        mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", "a.jpg", "image/jpeg", noisyJpeg(50, 50)))
                .param("kind", "PRODUCT_PHOTO")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- private links

    @Test
    void linksAreSignedAndExpire() throws Exception {
        JsonNode f = upload(OWNER, "PRODUCT_PHOTO", "a.jpg", noisyJpeg(200, 200), 201);
        String url = f.path("url").asString();
        String id = f.path("id").asString();
        mvc.perform(get(url.replaceAll("sig=[^&]+", "sig=AAAA"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LINK_INVALID"));
        // a longer life cannot be given to a link: the signature covers the time
        mvc.perform(get(url.replaceAll("exp=\\d+", "exp=" + (Instant.now().getEpochSecond() + 999_999))))
                .andExpect(status().isForbidden());
        // the main picture link does not open the thumbnail and the other way round
        mvc.perform(get(url + "&v=thumb")).andExpect(status().isForbidden());
        long past = Instant.now().getEpochSecond() - 5;
        mvc.perform(get("/api/files/" + id + "/content").param("exp", String.valueOf(past))
                        .param("sig", crypto.sign(id + "||" + past)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
        // fresh links whenever the file is asked for
        mvc.perform(get("/api/files/" + id).with(who.get(OWNER).bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());
        mvc.perform(get(url + "&download=true")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
    }

    @Test
    void otherBranchCannotSeeTheFile() throws Exception {
        JsonNode b2 = upload(OWNER, "BILL_PHOTO", "b2.jpg", noisyJpeg(100, 100), 201, "b2");
        mvc.perform(get("/api/files/" + b2.path("id").asString()).with(who.get(SALES).bearer())).andExpect(status().isNotFound());
        mvc.perform(get("/api/files/" + b2.path("id").asString()).with(who.get(ACCOUNTANT).bearer())).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- attach, discard, cleanup

    @Test
    void onlyTheUploaderRemovesAnUnsavedUpload() throws Exception {
        JsonNode f = upload(STAFF, "PRODUCT_PHOTO", "a.jpg", noisyJpeg(300, 200), 201);
        String id = f.path("id").asString();
        mvc.perform(delete("/api/files/" + id).with(who.get(SALES).bearer())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/files/" + id).with(who.get(STAFF).bearer())).andExpect(status().isNoContent());
        mvc.perform(get(f.path("url").asString())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from stored_file where public_id = ?", Integer.class, UUID.fromString(id))).isZero();
    }

    @Test
    void attachedFilesBelongToTheirRecord() throws Exception {
        JsonNode photo = upload(STAFF, "PRODUCT_PHOTO", "a.jpg", noisyJpeg(300, 200), 201);
        String id = photo.path("id").asString();
        // wrong kind
        mvc.perform(post("/api/test/files/attach").param("id", id).param("kind", "CERTIFICATE").param("ref", "GEM-09001")
                .with(who.get(STAFF).bearer())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_KIND"));
        mvc.perform(post("/api/test/files/attach").param("id", id).param("kind", "PRODUCT_PHOTO").param("ref", "GEM-09001")
                .with(who.get(STAFF).bearer())).andExpect(status().isOk());
        // not a second gem, and not removed from the form any more
        mvc.perform(post("/api/test/files/attach").param("id", id).param("kind", "PRODUCT_PHOTO").param("ref", "GEM-09002")
                .with(who.get(STAFF).bearer())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FILE_IN_USE"));
        mvc.perform(delete("/api/files/" + id).with(who.get(STAFF).bearer())).andExpect(status().isConflict());
        assertThat(files.ofOwner("GEM", "GEM-09001")).hasSize(1);
    }

    @Test
    void uploadsNeverSavedAreRemovedAfterADay() throws Exception {
        JsonNode old = upload(STAFF, "PRODUCT_PHOTO", "old.jpg", noisyJpeg(300, 200), 201);
        JsonNode fresh = upload(STAFF, "PRODUCT_PHOTO", "new.jpg", noisyJpeg(300, 200), 201);
        UUID oldId = UUID.fromString(old.path("id").asString());
        String key = jdbc.queryForObject("select storage_key from stored_file where public_id = ?", String.class, oldId);
        jdbc.update("update stored_file set created_at = now() - interval '25 hours' where public_id = ?", oldId);
        assertThat(storage.exists(key)).isTrue();
        assertThat(files.removeUnattached()).isGreaterThanOrEqualTo(1);
        assertThat(storage.exists(key)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from stored_file where public_id = ?", Integer.class,
                UUID.fromString(fresh.path("id").asString()))).isEqualTo(1);
    }

    @Test
    void fileNamesAreCleaned() {
        assertThat(FileService.cleanName("C:\\fakepath\\IMG_0042.HEIC.jpeg", FileType.JPEG)).isEqualTo("IMG_0042.HEIC.jpg");
        assertThat(FileService.cleanName("../../etc/passwd", FileType.PDF)).isEqualTo("passwd.pdf");
        assertThat(FileService.cleanName(null, FileType.PNG)).isEqualTo("file.png");
        assertThat(FileService.cleanName("a\"b\u0007.png", FileType.PNG)).isEqualTo("ab.png");
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode upload(String email, String kind, String name, byte[] bytes, int expect) throws Exception {
        return upload(email, kind, name, bytes, expect, null);
    }

    private JsonNode upload(String email, String kind, String name, byte[] bytes, int expect, String branch) throws Exception {
        var req = multipart("/api/files").file(new MockMultipartFile("file", name, "image/jpeg", bytes)).param("kind", kind)
                .with(who.get(email).bearer());
        if (branch != null) {
            req.header(CurrentUser.BRANCH_HEADER, branch);
        }
        String body = mvc.perform(req).andExpect(status().is(expect)).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    /** A photo with lots of detail (noise), so it is big like a real phone photo. */
    static byte[] noisyJpeg(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(42);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, r.nextInt(0xFFFFFF));
            }
        }
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillOval(w / 4, h / 4, w / 2, h / 2);
        g.dispose();
        return ImageProcessor.jpeg(img, 0.95f);
    }

    /** What a module does when its record is saved. */
    @RestController
    static class TestApi {
        private final FileService files;

        TestApi(FileService files) {
            this.files = files;
        }

        @PreAuthorize(Roles.ANY)
        @PostMapping("/api/test/files/attach")
        String attach(@RequestParam UUID id, @RequestParam FileKind kind, @RequestParam String ref) {
            files.attach(List.of(id), kind, "GEM", ref);
            return "ok";
        }
    }
}

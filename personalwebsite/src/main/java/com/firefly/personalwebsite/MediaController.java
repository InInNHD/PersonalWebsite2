package com.firefly.personalwebsite;

import java.io.IOException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestController
public class MediaController {
    private final MediaStore media;
    public MediaController(MediaStore media) { this.media=media; }
    @GetMapping("/api/admin/media")
    public MediaStore.Page list(@RequestParam(defaultValue="1") int page) { return media.list(page); }
    @PostMapping(value="/api/admin/media",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaStore.Asset upload(@RequestPart("file") MultipartFile file) throws IOException { return media.upload(file); }
    @GetMapping("/api/admin/media/{filename}/content")
    public ResponseEntity<FileSystemResource> preview(@PathVariable String filename) { return image(filename,true); }
    @GetMapping("/api/media/{filename}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String filename) { return image(filename,false); }
    private ResponseEntity<FileSystemResource> image(String filename,boolean admin) {
        var path=media.read(filename,admin);
        return ResponseEntity.ok().contentType(filename.endsWith(".png")?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG)
                // 撤回/回收后必须重新鉴权公开可见性，不缓存草稿转变之前的响应。
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .body(new FileSystemResource(path));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Void> tooLarge() { return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build(); }
}

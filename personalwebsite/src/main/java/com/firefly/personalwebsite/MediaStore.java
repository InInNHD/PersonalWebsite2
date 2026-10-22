package com.firefly.personalwebsite;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaStore {
    public static final long MAX_BYTES=5*1024*1024;
    private static final Pattern NAME=Pattern.compile("[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}\\.(png|jpg)");
    private final JdbcTemplate jdbc;
    private final Path root;
    public MediaStore(JdbcTemplate jdbc,@Value("${app.media.directory:./media}") String directory) {
        this.jdbc=jdbc; root=Path.of(directory).toAbsolutePath().normalize();
    }
    public record Asset(String id,String url,int width,int height,long byteSize) {}
    public record Page(List<Asset> items,long total,int page,long totalPages) {}
    private Asset map(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        return new Asset(rs.getString("id"),"/api/media/"+rs.getString("filename"),rs.getInt("width"),rs.getInt("height"),rs.getLong("byte_size"));
    }
    public Page list(int page) {
        if(page<1||page>1000000) throw bad("图片页码无效");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM media_asset",Long.class),pages=(total+11)/12;
        int actual=(int)Math.min(page,Math.max(1,pages));
        return new Page(jdbc.query("SELECT * FROM media_asset ORDER BY created_at DESC,id DESC LIMIT 12 OFFSET ?",this::map,(actual-1L)*12),total,actual,pages);
    }
    public Asset upload(MultipartFile file) throws IOException {
        if(file.isEmpty()||file.getSize()>MAX_BYTES) throw bad("图片大小须在1字节至5MiB之间");
        String original=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
        if(!original.matches(".*\\.(png|jpg|jpeg)$")) throw bad("仅接受PNG或JPEG图片");
        byte[] encoded;
        int width,height; String format;
        // 先读图片头检查像素数，再解码；不信任扩展名、MIME或客户端尺寸。
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw bad("文件不是有效的图片");
            var reader=readers.next();
            try {
                format=reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean png=format.equals("png"),jpeg=format.equals("jpeg");
                if(!png&&!jpeg) throw bad("图片内容必须是PNG或JPEG");
                if(png!=original.endsWith(".png")) throw bad("扩展名与图片内容不一致");
                String mime=file.getContentType();
                if(mime!=null&&!mime.equalsIgnoreCase(png?"image/png":"image/jpeg")) throw bad("图片MIME与内容不一致");
                reader.setInput(input,true,true); width=reader.getWidth(0); height=reader.getHeight(0);
                if(width<1||height<1||width>6000||height>6000||(long)width*height>20000000) throw bad("图片边长最多6000像素，总像素最多2000万");
                BufferedImage image=reader.read(0);
                var output=new ByteArrayOutputStream();
                if(!ImageIO.write(image,png?"png":"jpg",output)) throw bad("图片无法重新编码");
                // 重新编码移除元数据、尾随内容和原文件名。
                encoded=output.toByteArray(); format=png?"png":"jpg";
                if(encoded.length>MAX_BYTES) throw bad("重新编码后的图片超过5MiB，请压缩后重试");
            } finally { reader.dispose(); }
        } catch(javax.imageio.IIOException|IllegalArgumentException ex) { throw bad("图片损坏或内容不受支持"); }
        String id=UUID.randomUUID().toString(),filename=id+"."+format;
        Path target=path(filename);
        Files.createDirectories(root);
        Path temporary=Files.createTempFile(root,"upload-",".tmp");
        try {
            Files.write(temporary,encoded); Files.move(temporary,target);
            try { jdbc.update("INSERT INTO media_asset(id,filename,width,height,byte_size) VALUES (?,?,?,?,?)",id,filename,width,height,encoded.length); }
            catch(RuntimeException ex) { Files.deleteIfExists(target); throw ex; }
        } finally { Files.deleteIfExists(temporary); }
        return new Asset(id,"/api/media/"+filename,width,height,encoded.length);
    }
    // 固定名称规则、目录边界及符号链接检查；不允许客户端指定任意磁盘路径。
    public Path path(String filename) {
        if(!NAME.matcher(filename).matches()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path target=root.resolve(filename).normalize();
        if(!target.getParent().equals(root)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        for(Path p=target;p!=null;p=p.getParent()) if(Files.isSymbolicLink(p)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return target;
    }
    public String localFilename(String url) {
        if(url==null||!url.startsWith("/api/media/")) return null;
        String filename;
        try { filename=java.net.URI.create(url).getRawPath().substring("/api/media/".length()); }
        catch(IllegalArgumentException ex) { throw bad("本站图片地址无效"); }
        if(!NAME.matcher(filename).matches()) throw bad("本站图片地址无效");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM media_asset WHERE filename=?",Integer.class,filename)==0) throw bad("所选图片不存在");
        return filename;
    }
    // 与文章保存在同一事务内，编辑正文和封面时同步重建关联。
    public void replaceReferences(long articleId,String cover,List<String> bodyMediaUrls) {
        var roles=new LinkedHashMap<String,Set<String>>();
        String filename=localFilename(cover);
        if(filename!=null) roles.computeIfAbsent(filename,k->new HashSet<>()).add("COVER");
        // 管理端使用现有Markdown解析器提取真实图片目标，再明确提交授权关联。
        // 不扫描任意正文子串，避免代码示例、外部URL参数误使私人图片公开。
        for(String url:bodyMediaUrls==null?List.<String>of():bodyMediaUrls) {
            String name=localFilename(url);
            if(name==null) throw bad("正文媒体关联必须是本站上传图片地址");
            roles.computeIfAbsent(name,k->new HashSet<>()).add("BODY");
        }
        jdbc.update("DELETE FROM article_media WHERE article_id=?",articleId);
        roles.forEach((name,uses)->uses.forEach(role->jdbc.update("INSERT INTO article_media(article_id,media_id,usage_role) SELECT ?,id,? FROM media_asset WHERE filename=?",articleId,role,name)));
    }
    public Path read(String filename,boolean admin) {
        Path file=path(filename);
        String sql=admin?"SELECT COUNT(*) FROM media_asset WHERE filename=?":"SELECT COUNT(*) FROM media_asset m WHERE m.filename=? AND EXISTS (SELECT 1 FROM article_media r JOIN article a ON a.id=r.article_id WHERE r.media_id=m.id AND a.status='PUBLISHED' AND a.deleted_at IS NULL)";
        if(jdbc.queryForObject(sql,Integer.class,filename)==0||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return file;
    }
    private ResponseStatusException bad(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,reason); }
}

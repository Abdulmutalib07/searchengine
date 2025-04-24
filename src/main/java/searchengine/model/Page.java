package searchengine.model;

import lombok.Data;

import javax.persistence.Index;
import javax.persistence.*;
import java.util.List;

@Data
@Entity
@Table(name = "page", indexes = {
        @Index(name = "inx_path", columnList = "path")
})
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;
    @Column(name = "path", nullable = false, columnDefinition = "TEXT")
    private String path;
    @Column(name = "code", nullable = false)
    private int code;
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
}

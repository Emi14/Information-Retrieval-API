package dashboard.core;


import dashboard.core.analyzer.RomanianAnalyzerWithASCIIFolding;
import dashboard.utils.Constants;
import lombok.Getter;
import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Created by Ionut Emanuel Mihailescu on 3/19/18.
 */
@Component
@Getter
public class Indexer implements AutoCloseable {
    public static final String EMPTY_STRING = "";
    private static final Logger LOGGER = Logger.getLogger(Indexer.class);
    private static final String ADDING_FILE_TO_INDEX = "Adding file %s to index.";
    private static final String BUILDING_THE_INDEX = "Starting building the index.";
    private static final String EMPTY_DIRECTORY_ERROR =
            "Empty directory, nothing to index. Please, provide another directory.";
    private static final String INDEX_BUILD_SUCCESS = "Index build successfully. %d documents were been added.";
    private static final String PARSING_ERROR = "Error while parsing the file %s";
    private IndexWriter indexWriter;

    @Value("${index.directory.path}")
    private String indexDirectoryPath;

    @Value("${documents.directory.path}")
    private String documentsDirectoryPath;

    @PostConstruct
    private void createIndexWriter() throws IOException {
        Directory indexDirectory = FSDirectory.open(Paths.get(indexDirectoryPath));
        RomanianAnalyzerWithASCIIFolding romanianAnalyzer = new RomanianAnalyzerWithASCIIFolding();
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(romanianAnalyzer);
        indexWriter = new IndexWriter(indexDirectory, indexWriterConfig);
        buildIndex(documentsDirectoryPath);
        indexWriter.commit();
    }

    private Document buildDocument(File file) {
        Document document = new Document();

        FieldType fieldType = new FieldType();
        fieldType.setStored(true);
        fieldType.setStoreTermVectors(true);
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS);

        enrichDocument(file, document, fieldType);

        return document;
    }

    private void enrichDocument(File file, Document document, FieldType fieldType) {
        document.add(new Field(Constants.CONTENT, getContentFromFile(file), fieldType));
        document.add(new StringField(Constants.FILE_NAME, file.getName(), Field.Store.YES));
        document.add(new StringField(Constants.PATH, file.getAbsolutePath(), Field.Store.YES));
    }

    private String getContentFromFile(File file) {
        Tika tika = new Tika();
        String parsedFile = EMPTY_STRING;
        try {
            parsedFile = tika.parseToString(file);
        } catch (TikaException | IOException e) {
            LOGGER.error(String.format(PARSING_ERROR, e));
        }
        return parsedFile;
    }

    private void addFileToIndex(File file) throws IOException {
        LOGGER.info(String.format(ADDING_FILE_TO_INDEX, file.getAbsolutePath()));

        indexWriter.addDocument(buildDocument(file));
    }

    private void buildIndex(String directoryPathToBeIndexed) throws IOException {
        LOGGER.info(BUILDING_THE_INDEX);

        File[] files = new File(directoryPathToBeIndexed).listFiles();

        if (Objects.isNull(files)) {
            LOGGER.error(EMPTY_DIRECTORY_ERROR);
        } else {
            for (File file : files) {
                if (file.isDirectory()) {
                    buildIndex(file.getAbsolutePath());
                }
                if (isProcessableFile(file)) {
                    addFileToIndex(file);
                }
            }
        }
        LOGGER.info(String.format(INDEX_BUILD_SUCCESS, indexWriter.numDocs()));
    }

    private boolean isProcessableFile(File file) {
        return file.canRead() && file.exists();
    }

    @PreDestroy
    @Override
    public void close() throws Exception {
        indexWriter.commit();
        indexWriter.close();
    }
}

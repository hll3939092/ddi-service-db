package uk.ac.ebi.ddi.service.db.service.enrichment;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import uk.ac.ebi.ddi.service.db.exception.DBWriteException;
import uk.ac.ebi.ddi.service.db.model.enrichment.Synonym;
import uk.ac.ebi.ddi.service.db.repo.enrichment.ISynonymsRepo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mingze on 30/07/15.
 */

//@Component
@Service
public class SynonymsService implements ISynonymsService {


    @Autowired
    private ISynonymsRepo accessRepo;

    /**
     * intert  a single word in database
     *
     * @param word the word to be inserted in the database
     * @return
     */
    @Override
    public Synonym insert(String word, List<String> synonyms) {
        Synonym synonym = new Synonym(word, synonyms);
        return accessRepo.insert(synonym);
    }

//    /**
//     * insert one word as a synonym of the main word
//     *
//     * @param mainWord     of all synonyms (basically, every word in synonyms cycle could be the mainWord now)
//     * @param synonymLabel the new synonym which need to be inserted in the synonyms cycle
//     * @return
//     */
//    @Override
//    public Synonym insertAsSynonym(Synonym mainWord, String synonymLabel) {
//
//        if ((mainWord.getId() == null))
//            throw new DBWriteException(" The reference to the original resource should contain an Id");
//
//        System.out.println("mainword: " + mainWord.getlabel() + " Sysnonym: " + synonymLabel);
//
////        if (accessRepo.findOne(mainWord.getId()) == null) {
////            throw new DBWriteException("Can not find mainWord(" + mainWord + ") in this DB");
////        }
//
//        Synonym synonym = accessRepo.findByLabelQuery(synonymLabel);
//        if (synonym != null) {
//            return synonym;
//        }
//
//        synonym = new Synonym(synonymLabel);
//        Synonym insertedWord = accessRepo.insert(synonym);
//        if ((synonym.getId() == null))
//            throw new DBWriteException("Insert failed, no _id assigned to this synonym");
//        insertedWord.setNextSynonym(mainWord.getNextSynonym());
//        mainWord.setNextSynonym(insertedWord.getId());
//        update(mainWord);
//        update(insertedWord);
//
//        return insertedWord;
//    }

    /**
     * read on word by id
     *
     * @param id of the word entry in database
     * @return
     */
    @Override
    public Synonym read(ObjectId id) {
        return accessRepo.findOne(id);
    }

    /**
     * read all words by pagination
     *
     * @param pageStart
     * @param size
     * @return
     */
    @Override
    public Page<Synonym> readAll(int pageStart, int size) {
        return accessRepo.findAll(new PageRequest(pageStart, size));
    }

    /**
     * update the information of one word
     *
     * @param synonym the new datasetAccess information
     * @return
     */
    @Override
    public Synonym update(Synonym synonym) {
        return accessRepo.save(synonym);
    }

    /**
     * delete a word by id
     *
     * @param id Identifier of the word to be removed from the database
     * @return
     */
    @Override
    public Synonym delete(ObjectId id) {
        accessRepo.delete(id);
        return accessRepo.findOne(id);
    }

    /**
     * read a word by label
     *
     * @param label
     * @return
     */
    @Override
    public Synonym readByLabel(String label) {
        if ((label == null))
            throw new DBWriteException(" The reference to the original resource should contain a string");

        return accessRepo.findByLabelQuery(label);

    }

    /**
     * get all sysnonyms of one word's label
     *
     * @param wordLabel
     * @return
     */
    @Override
    public List<String> getAllSynonyms(String wordLabel) {
        Synonym synonym = accessRepo.findByLabelQuery(wordLabel);
        if(synonym != null)
            return synonym.getSynonyms();
        return null;
    }

    private ArrayList<String> mergeLowerAndUpperCase(ArrayList<String> wordList) {
        ArrayList<String> cloneList = new ArrayList<>();
        ArrayList<String> cloneListInLower = new ArrayList<>();
        for (String word : wordList) {
            if(!cloneListInLower.contains(word.toLowerCase())) {
                cloneListInLower.add(word.toLowerCase());
                cloneList.add(word);
            }
        }

        return cloneList;
    }

    /**
     * check if a word(label) is exist
     *
     * @param wordLabel
     * @return
     */
    @Override
    public boolean isWordExist(String wordLabel) {
        Synonym mainWord = accessRepo.findByLabelQuery(wordLabel);
        return (mainWord != null);
    }

}

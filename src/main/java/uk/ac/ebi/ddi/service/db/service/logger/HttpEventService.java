package uk.ac.ebi.ddi.service.db.service.logger;


import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.stereotype.Component;
import uk.ac.ebi.ddi.service.db.exception.DBWriteException;
import uk.ac.ebi.ddi.service.db.model.dataset.Dataset;
import uk.ac.ebi.ddi.service.db.model.dataset.MostAccessedDatasets;
import uk.ac.ebi.ddi.service.db.model.dataset.Scores;
import uk.ac.ebi.ddi.service.db.model.logger.AbstractResource;
import uk.ac.ebi.ddi.service.db.model.logger.DatasetResource;
import uk.ac.ebi.ddi.service.db.model.logger.HttpEvent;
import uk.ac.ebi.ddi.service.db.model.logger.ResourceStatVisit;
import uk.ac.ebi.ddi.service.db.repo.logger.IDatasetResourceRepo;
import uk.ac.ebi.ddi.service.db.repo.logger.IHttpEventRepo;
import uk.ac.ebi.ddi.service.db.service.database.DatabaseDetailService;
import uk.ac.ebi.ddi.service.db.service.dataset.DatasetService;
import uk.ac.ebi.ddi.service.db.service.dataset.MostAccessedDatasetService;
import uk.ac.ebi.ddi.service.db.utils.Constants;
import uk.ac.ebi.ddi.service.db.utils.Tuple;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.Fields.fields;


/**
 * @author Yasset Perez-Riverol (ypriverol@gmail.com)
 * @date 29/04/2015
 */

@Component
public class HttpEventService implements IHttpEventService {

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    private IHttpEventRepo accessRepo;

    @Autowired
    private IDatasetResourceRepo datasetRepo;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private MostAccessedDatasetService mostAccessedDatasetService;

    @Autowired
    private DatabaseDetailService databaseDetailService;

    @Override
    public HttpEvent insert(HttpEvent httpEvent) {
        if((httpEvent.getResource() != null && httpEvent.getResource().getId() == null))
            throw new DBWriteException(" The reference to the original resource should contain an Id");

        return accessRepo.insert(httpEvent);
    }


    public HttpEvent save(HttpEvent httpEvent) {
        if((httpEvent.getResource() != null && httpEvent.getResource().getId() == null))
            throw new DBWriteException(" The reference to the original resource should contain an Id");
        return accessRepo.save(httpEvent);
    }


    @Override
    public HttpEvent read(ObjectId id) {
        return accessRepo.findOne(id);
    }

    @Override
    public Page<HttpEvent> readAll(int pageStart, int size) {
        return accessRepo.findAll(new PageRequest(pageStart, size));
    }

    @Override
    public HttpEvent update(HttpEvent httpEvent) {

        HttpEvent existingHttpEvent = accessRepo.findOne(httpEvent.getId());

        existingHttpEvent.setAccessDate(httpEvent.getAccessDate());
        existingHttpEvent.setHost(httpEvent.getHost());
        existingHttpEvent.setPath(httpEvent.getPath());
        existingHttpEvent.setReferrer(httpEvent.getReferrer());
        existingHttpEvent.setRequest(httpEvent.getRequest());
        existingHttpEvent.setResponse_size(httpEvent.getResponse_size());
        existingHttpEvent.setStatus(httpEvent.getStatus());
        existingHttpEvent.setUser(httpEvent.getUser());
        existingHttpEvent.setUserAgent(httpEvent.getUserAgent());
        existingHttpEvent.setLogSource(httpEvent.getLogSource());
        existingHttpEvent.setRawMessage(httpEvent.getRawMessage());
        existingHttpEvent.setResource((AbstractResource) httpEvent.getResource());

        return save(existingHttpEvent);
    }

    @Override
    public HttpEvent delete(ObjectId id) {
        accessRepo.delete(id);
        return accessRepo.findOne(id);
    }

    public long getLongEventService( String acccesion, String database){
       return accessRepo.getNumberEventByHttpEventDataSetResource(acccesion, database);
    }

    public List<HttpEvent> getHttpEventbyResource(AbstractResource resource){
        return  accessRepo.findByAbstractResource(resource);
    }

    public long getEventByResourceId(ObjectId _id){
        return accessRepo.getNumberEventByDataResource(_id);
    }

    public Map<Tuple<String, String>, Integer> moreAccessedDatasetResource(int size){
        Map<Tuple<String, String>, Integer> datasets = new HashMap<>();
        List<ResourceStatVisit> currentMostAccessed = accessRepo.getHttpEventByDatasetResource(size);
        //Todo: It would be interesting to to this in batch
        for(ResourceStatVisit visit: currentMostAccessed){
            if(visit.getAbstractResource() != null){
                DatasetResource resource = datasetRepo.findByIdDatabaseQuery(visit.getAbstractResource().getId());
                Tuple<String, String> resourceMap = new Tuple<>(resource.getAccession(), resource.getDatabase());
                datasets.put(resourceMap,visit.getTotal());
            }
        }

        return datasets;
    }

    public void moreAccessedDataset(int size){
        try{
        AggregationOptions options = Aggregation.newAggregationOptions().allowDiskUse(true).build();

        Aggregation agg = Aggregation.newAggregation(
                group("abstractResource._id").count().as("total")
                        .first("abstractResource.accession").as("accession")
                        .first("abstractResource.database").as("database"),
                project()//.andExpression("_id").as("mostAccessedDatasets._id")
                        .andInclude("total","accession","database"),
                sort(Sort.Direction.DESC, "total")//,
                ,limit(150000)
        ).withOptions(options);

        //try {
        //Convert the aggregation result into a List
        AggregationResults<MostAccessedDatasets> groupResults
                = mongoTemplate.aggregate(agg, Constants.LOGGER_COLLECTION, MostAccessedDatasets.class);
        List<MostAccessedDatasets> currentMostAccessed = groupResults.getMappedResults();
        mostAccessedDatasetService.deleteAll();

            for (MostAccessedDatasets visit : currentMostAccessed) {
                if (visit.getId() != null) {
                    String database = databaseDetailService.retriveAnchorName(visit.getDatabase());
                    Dataset datasetOut = datasetService.read(visit.getAccession(),database);
                    if (datasetOut != null) {

                        if(datasetOut.getScores() != null) {
                            datasetOut.getScores().setViewCount(visit.getTotal());
                        }else{
                            Scores scores = new Scores();
                            scores.setViewCount(visit.getTotal());
                            datasetOut.setScores(scores);
                        }
                        HashSet<String> count = new HashSet<String>();
                        count.add(String.valueOf(visit.getTotal()));
                        datasetOut.getAdditional().put(Constants.VIEWCOUNT_FIELD,count);
                        datasetService.update(datasetOut.getId(),datasetOut);
                        MostAccessedDatasets dataset = new MostAccessedDatasets(datasetOut, visit.getTotal());
                        mostAccessedDatasetService.save(dataset);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            System.out.println(ex.getMessage());
        }

    }
}

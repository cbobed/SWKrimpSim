///////////////////////////////////////////////////////////////////////////////
// File: CodifyingTest.java 
// Author: Carlos Bobed
// Date: June 2017
// Comments: Adaptation of the KrimpAlgorithm main method to test whether 
// 		we are losing any possible information in the process
// Modifications: 
///////////////////////////////////////////////////////////////////////////////

package com.irisa.swpatterns.krimp.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.jena.rdf.model.Resource;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.irisa.jenautils.BaseRDF;
import com.irisa.jenautils.QueryResultIterator;
import com.irisa.jenautils.UtilOntology;
import com.irisa.jenautils.BaseRDF.MODE;
import com.irisa.swpatterns.FrequentItemSetExtractor;
import com.irisa.swpatterns.TransactionsExtractor;
import com.irisa.swpatterns.Utils;
import com.irisa.swpatterns.data.AttributeIndex;
import com.irisa.swpatterns.data.ItemsetSet;
import com.irisa.swpatterns.data.LabeledTransactions;
import com.irisa.swpatterns.krimp.CodeTable;
import com.irisa.swpatterns.krimp.KrimpAlgorithm;

import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;

public class CodifyingTest {

	private static Logger logger = Logger.getLogger(CodifyingTest.class);

	
	public static void main(String[] args) {
		BasicConfigurator.configure();
		PropertyConfigurator.configure("log4j-config.txt");

		// Setting up options
		CommandLineParser parser = new DefaultParser();
		Options options = new Options();
		options.addOption("file", true, "RDF file");
		// options.addOption("otherFile", true, "Other RDF file");
		options.addOption("endpoint", true, "Endpoint adress");
		options.addOption("output", true, "Output csv file");
		options.addOption("limit", true, "Limit to the number of individuals extracted");
		options.addOption("resultWindow", true, "Size of the result window used to query servers.");
		options.addOption("classPattern", true, "Substring contained by the class uris.");
		options.addOption("noOut", false, "Not taking OUT properties into account.");
		options.addOption("noIn", false, "Not taking IN properties into account.");
		options.addOption("noTypes", false, "Not taking TYPES into account.");
		options.addOption("FPClose", false, "Use FPClose algorithm. (default)");
		options.addOption("FPMax", false, "Use FPMax algorithm.");
		options.addOption("FIN", false, "Use FIN algorithm.");
		options.addOption("Relim", false, "Use Relim algorithm.");
		options.addOption("class", true, "Class of the studied individuals.");
		options.addOption("rank1", false, "Extract informations up to rank 1 (types, out-going and in-going properties and object types), default is only types, out-going and in-going properties.");
		options.addOption("transactionFile", false, "Only create a .dat transaction for each given file.");
		options.addOption("path", true, "Extract paths of length N.");
		options.addOption("help", false, "Display this help.");
		options.addOption("inputTransaction", true, "Transaction file (RDF data will be ignored).");
		// added for pruning 
		options.addOption("pruning", false, "Activate post-acceptance pruning"); 

		// Setting up options and constants etc.
		UtilOntology onto = new UtilOntology();
		try {
			CommandLine cmd = parser.parse( options, args);

			boolean helpAsked = cmd.hasOption("help");
			if(helpAsked) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp( "RDFtoTransactionConverter", options );
			} else {
				TransactionsExtractor converter = new TransactionsExtractor();
				FrequentItemSetExtractor fsExtractor = new FrequentItemSetExtractor();
				ItemsetSet realtransactions ;
				Itemsets codes = null;
				
				boolean activatePruning = false; 

				String filename = cmd.getOptionValue("file");
//				String otherFilename = cmd.getOptionValue("otherFile");
				String endpoint = cmd.getOptionValue("endpoint"); 
				String output = cmd.getOptionValue("output"); 
				String limitString = cmd.getOptionValue("limit");
				String resultWindow = cmd.getOptionValue("resultWindow");
				String classRegex = cmd.getOptionValue("classPattern");
				String className = cmd.getOptionValue("class");
				String pathOption = cmd.getOptionValue("path");
				converter.setNoTypeTriples( cmd.hasOption("noTypes") || converter.noTypeTriples());
				converter.noInTriples(cmd.hasOption("noIn") || converter.noInTriples());
				converter.setNoOutTriples(cmd.hasOption("noOut") || converter.noOutTriples());
				
				activatePruning = cmd.hasOption("pruning"); 
				
				if(cmd.hasOption("FPClose")) {
					fsExtractor.setAlgoFPClose();
				}
				if(cmd.hasOption("FPMax")) {
					fsExtractor.setAlgoFPMax();
				}
				if(cmd.hasOption("FIN")) {
					fsExtractor.setAlgoFIN();
				}
				if(cmd.hasOption("Relim")) {
					fsExtractor.setAlgoRelim();
				}
				converter.setRankOne(cmd.hasOption("rank1") || converter.isRankOne());
				logger.debug("output: " + output + " limit:" + limitString + " resultWindow:" + resultWindow + " classpattern:" + classRegex + " noType:" + converter.noTypeTriples() + " noOut:" + converter.noOutTriples() + " noIn:"+ converter.noInTriples());
				logger.debug("Pruning activated: "+activatePruning);
			
				
				if(!cmd.hasOption("inputTransaction")) {
					if(limitString != null) {
						QueryResultIterator.setDefaultLimit(Integer.valueOf(limitString));
					}
					if(resultWindow != null) {
						QueryResultIterator.setDefaultLimit(Integer.valueOf(resultWindow));
					}
					if(cmd.hasOption("classPattern")) {
						UtilOntology.setClassRegex(classRegex);
					} else {
						UtilOntology.setClassRegex(null);
					}

					if(pathOption != null) {
						converter.setPathsLength(Integer.valueOf(pathOption));
					}

					BaseRDF baseRDF = null;
					if(filename != null) {
						baseRDF = new BaseRDF(filename, MODE.LOCAL);
					} else if (endpoint != null){
						baseRDF = new BaseRDF(endpoint, MODE.DISTANT);
					}

					logger.debug("initOnto");
					onto.init(baseRDF);

					logger.debug("extract");
					// Extracting transactions

					LabeledTransactions transactions;

					if(cmd.hasOption("class")) {
						Resource classRes = onto.getModel().createResource(className);
						transactions = converter.extractTransactionsForClass(baseRDF, onto, classRes);
					} else if(cmd.hasOption("path")) {
						transactions = converter.extractPathAttributes(baseRDF, onto);
					} else {
						transactions = converter.extractTransactions(baseRDF, onto);
					}

					AttributeIndex index = converter.getIndex();

					// Printing transactions
					if(cmd.hasOption("transactionFile")) {
						index.printTransactionsItems(transactions, filename + ".dat");

//						if(cmd.hasOption("otherFile")) {
//							index.printTransactionsItems(transactions, otherFilename + ".dat");
//						}
						
					}

					realtransactions = index.convertToTransactions(transactions);
					codes = fsExtractor.computeItemsets(transactions, index);
					logger.debug("Nb Lines: " + realtransactions.size());

					if(cmd.hasOption("transactionFile")) {
						index.printTransactionsItems(transactions, filename + ".dat");
					}
					logger.debug("Nb items: " + converter.getIndex().size());

					baseRDF.close();

				} else {
					realtransactions = new ItemsetSet(Utils.readTransactionFile(cmd.getOptionValue("inputTransaction")));
					codes = fsExtractor.computeItemsets(realtransactions);
					logger.debug("Nb Lines: " + realtransactions.size());
				}
				ItemsetSet realcodes = new ItemsetSet(codes);

				try {
					CodeTable standardCT = CodeTable.createStandardCodeTable(realtransactions );

					KrimpAlgorithm kAlgo = new KrimpAlgorithm(realtransactions, realcodes);
					CodeTable krimpCT = kAlgo.runAlgorithm(activatePruning);
					double normalSize = standardCT.totalCompressedSize();
					double compressedSize = krimpCT.totalCompressedSize();
					logger.debug("-------- FIRST RESULT ---------");
					logger.debug(krimpCT);
					//					logger.debug("First Code table: " + krimpCT);
					logger.debug("First NormalLength: " + normalSize);
					logger.debug("First CompressedLength: " + compressedSize);
					logger.debug("First Compression: " + (compressedSize / normalSize));
					
					ArrayList<HashSet<Integer>> codifiedDatabase = new ArrayList<>();  

					logger.debug("-------- CODIFICATION --------");
					long isCoverTime = 0; 
					long codifyCoverageTime = 0; 
					long startTime = 0; 
					ItemsetSet codification = null; 
					for (Itemset trans: realtransactions) {
						startTime = System.nanoTime(); 
						krimpCT.codifyUsingIsCover(trans); 
						isCoverTime += (System.nanoTime()-startTime); 
						
						startTime = System.nanoTime();
						codification = krimpCT.codify(trans); 
						codifyCoverageTime += (System.nanoTime()-startTime); 
						
						HashSet<Integer> cod = new HashSet<>(); 
						for (Itemset it: codification) {
							cod.add(krimpCT.getCodeIndice(it)); 
						}
						codifiedDatabase.add(cod); 
					}
					
					logger.debug("Codified: "+codifiedDatabase.size()+" transactions");
					logger.debug("isCoverTime: "+((double)isCoverTime/(double)1_000_000_000));
					logger.debug("codifyTime: "+((double)codifyCoverageTime/(double)1_000_000_000));
					
					logger.debug("-------- RECONSTRUCTING --------");
					ItemsetSet reconstructedTransactions = new ItemsetSet() ;
					for (HashSet<Integer> codifiedTrans: codifiedDatabase) {
						HashSet<Integer> reconstructedTrans = new HashSet<>(); 
						int[] auxContainer = null; 
						for (Integer codifiedItemset: codifiedTrans) {
							auxContainer = krimpCT.getCodeFromIndex(codifiedItemset).itemset; 
							for (int i=0; i<auxContainer.length; i++) {
								reconstructedTrans.add(auxContainer[i]); 
							}
						}
						auxContainer = new int[reconstructedTrans.size()]; 
						int j= 0; 
						for (Integer auxInt: reconstructedTrans) {
							auxContainer[j] = auxInt; 
							j++; 
						}
						Arrays.sort(auxContainer);
						Itemset reconstructedItemset = new Itemset(auxContainer); 
						reconstructedTransactions.add(reconstructedItemset); 
						
					}
					
					logger.debug("reconstructedTransactions size: "+reconstructedTransactions.size());
					logger.debug("realTransactions size: "+realtransactions.size());
					logger.debug("Equals ? " + realtransactions.equals(reconstructedTransactions.size()));
					
					realtransactions.sort(CodeTable.standardCandidateOrderComparator);
					reconstructedTransactions.sort(CodeTable.standardCandidateOrderComparator);
					
					logger.debug("Equals ? " + realtransactions.equals(reconstructedTransactions.size()));
					
					logger.debug("REAL:::");
					logger.debug(realtransactions);
					logger.debug("RECONSTRUCTED:::");
					logger.debug(reconstructedTransactions);
					
				} catch (Exception e) {
					logger.fatal("RAAAH", e);
				}

			}
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		onto.close();
	}
	
}
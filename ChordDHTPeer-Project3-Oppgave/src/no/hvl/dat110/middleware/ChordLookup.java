/**
 * 
 */
package no.hvl.dat110.middleware;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.Hash;
import no.hvl.dat110.util.Util;

/**
 * @author tdoy
 *
 */
public class ChordLookup {

	private Node node;
	
	public ChordLookup(Node node) {
		this.node = node;
	}
	
	public NodeInterface findSuccessor(BigInteger key) throws RemoteException {
		NodeInterface successor = node.getSuccessor();
		NodeInterface successorStub = Util.getProcessStub(successor.getNodeName(), successor.getPort());
		if (successorStub != null) {
			BigInteger lower = node.getNodeID().add(BigInteger.valueOf(1));
			BigInteger upper = successorStub.getNodeID();
			if (Util.computeLogic(key, lower, upper)) {
				System.out.println(successorStub.getNodeID());
				return successorStub;
			} else {
				System.out.println("test");
				NodeInterface highest_pred = findHighestPredecessor(key);
				return highest_pred.findSuccessor(key);
			}
		}
		return null;
	}
	
	/**
	 * This method makes a remote call. Invoked from a local client
	 * @param key BigInteger
	 * @return
	 * @throws RemoteException
	 */
	private NodeInterface findHighestPredecessor(BigInteger key) throws RemoteException {

		List<NodeInterface> table = node.getFingerTable();

		for (int i = table.size()-2; i > 0; i--) {
			NodeInterface finger = table.get(i);
			NodeInterface fingerSuccessor = Util.getProcessStub(finger.getNodeName(), finger.getPort());

			BigInteger lower = node.getNodeID().add(BigInteger.valueOf(1));
			BigInteger upper = key.subtract(BigInteger.valueOf(1));

			if (Util.computeLogic(finger.getNodeID(), lower, upper)) {
				return fingerSuccessor;
			}
		}
		return (NodeInterface) node;
	}
	
	public void copyKeysFromSuccessor(NodeInterface succ) {
		
		Set<BigInteger> filekeys;
		try {
			// if this node and succ are the same, don't do anything
			if(succ.getNodeName().equals(node.getNodeName()))
				return;
			
			System.out.println("copy file keys that are <= "+node.getNodeName()+" from successor "+ succ.getNodeName()+" to "+node.getNodeName());
			
			filekeys = new HashSet<>(succ.getNodeKeys());
			BigInteger nodeID = node.getNodeID();
			BigInteger succID = succ.getNodeID();
			
			for(BigInteger fileID : filekeys) {
				// a small modification here if node > succ. We need to make sure the keys copied are only lower than succ
				if(succ.getNodeID().compareTo(nodeID) == -1) {
					if(fileID.compareTo(succID) == -1 || fileID.compareTo(succID) == 0) {
						BigInteger addresssize = Hash.addressSize();
						fileID = fileID.add(addresssize);
					}
				}
				// if fileID <= nodeID, copy the file to the newly joined node.
				if(fileID.compareTo(nodeID) == -1 || fileID.compareTo(nodeID) == 0) {
					System.out.println("fileID="+fileID+" | nodeID= "+nodeID);
					node.addKey(fileID); 															// re-assign file to this successor node
					Message msg = succ.getFilesMetadata().get(fileID);				
					node.saveFileContent(msg.getNameOfFile(), fileID, msg.getBytesOfFile(), msg.isPrimaryServer()); 			// save the file in memory of the newly joined node
					succ.removeKey(fileID); 	 																				// remove the file key from the successor
					succ.getFilesMetadata().remove(fileID); 																	// also remove the saved file from memory
				}
			}
			
			System.out.println("Finished copying file keys from successor "+ succ.getNodeName()+" to "+node.getNodeName());
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public void notify(NodeInterface pred_new) throws RemoteException {
		
		NodeInterface pred_old = node.getPredecessor();
		
		// if the predecessor is null accept the new predecessor
		if(pred_old == null) {
			node.setPredecessor(pred_new);		// accept the new predecessor
			return;
		}
		
		else if(pred_new.getNodeName().equals(node.getNodeName())) {
			node.setPredecessor(null);
			return;
		} else {
			BigInteger nodeID = node.getNodeID();
			BigInteger pred_oldID = pred_old.getNodeID();
			
			BigInteger pred_newID = pred_new.getNodeID();
			
			// check that pred_new is between pred_old and this node, accept pred_new as the new predecessor
			// check that ftsuccID is a member of the set {nodeID+1,...,ID-1}
			boolean cond = Util.computeLogic(pred_newID, pred_oldID.add(new BigInteger("1")), nodeID.add(new BigInteger("1")));
			if(cond) {		
				node.setPredecessor(pred_new);		// accept the new predecessor
			}	
		}		
	}
	
	public void leaveRing() throws RemoteException {
		
		System.out.println("Attempting to update successor and predecessor before leaving the ring...");
		
		try {
		 
			NodeInterface prednode = node.getPredecessor();														// get the predecessor			
			NodeInterface succnode = node.getSuccessor();														// get the successor		
			NodeInterface prednodestub = Util.getProcessStub(prednode.getNodeName(), prednode.getPort());		// get the prednode stub			
			NodeInterface succnodestub = Util.getProcessStub(succnode.getNodeName(), succnode.getPort());		// get the succnode stub			
			Set<BigInteger> keyids = node.getNodeKeys();									// get the keys for chordnode
						 
			if(succnodestub != null) {												// add chordnode's keys to its successor
				keyids.forEach(fileID -> {
					try {
						System.out.println("Adding fileID = "+fileID+" to "+succnodestub.getNodeName());
						succnodestub.addKey(fileID);
						Message msg = node.getFilesMetadata().get(fileID);				
						succnodestub.saveFileContent(msg.getNameOfFile(), fileID, msg.getBytesOfFile(), msg.isPrimaryServer()); 			// save the file in memory of the newly joined node
					} catch (RemoteException e) {
						//e.printStackTrace();
					} 
				});

				succnodestub.setPredecessor(prednodestub); 							// set prednode as the predecessor of succnode
			}
			if(prednodestub != null) {
				prednodestub.setSuccessor(succnodestub);							// set succnode as the successor of prednode			
			} 
		}catch(Exception e) {
			//
			System.out.println("some errors while updating succ/pred/keys...");
		}
		System.out.println("Update of successor and predecessor completed...bye!");
	}

}

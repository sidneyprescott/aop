/**
 * * Created by guro saria on 26-11-2015.
 *
 * source code for agent that control racetrack/field
 */
import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.util.Logger;

import java.util.ArrayList;
import java.util.logging.Level;

import static java.lang.Math.abs;

public class TrackAgent extends Agent {
    //-1 is occupied, -2 is offtrack, anything else is free
    //TODO: map should be created, this is test map
    private int[][] map = {{0, 1, 2, 3, 4, 5 ,0},
                          {0, -2, -2, -2, -2, -2 ,1},
                            {2, 1, 0, 5, 4, 3 ,2},
                            {0, 1, 2, 0, 5, 4 ,3}};

    //oneday maybe we will use ondisk database, but for now:
    //will hold positions etc
    private ArrayList<Competitor> competitors = new ArrayList<Competitor>();

    public final int mapHeight = 3;
    public final int mapWidth = 6;

    private final int MAXSTEPS = 5;

    private Logger myLogger = Logger.getMyLogger(getClass().getName());

    private class FieldBehaviour extends CyclicBehaviour {

        public FieldBehaviour(Agent a) {
            super(a);
        }

        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null && msg.getPerformative() == ACLMessage.REQUEST)
                processMessage(msg);
            else
                block();
        }
    }


    protected void setup() {
        DFAgentDescription dfd = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("FieldAgent");
        sd.setName(getName());
        sd.setOwnership("de staat");
        dfd.setName(getAID());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            FieldBehaviour PingBehaviour = new FieldBehaviour(this);
            addBehaviour(PingBehaviour);
        } catch (FIPAException e) {
            myLogger.log(Logger.SEVERE, "Agent " + getLocalName() + " - Cannot register with DF", e);
            doDelete();
        }
    }

    private void processMessage(ACLMessage msg) {
        int [] coordinates;
        int tileValue;
        ACLMessage reply = msg.createReply();
        AID sender = msg.getSender();
        if (msg.getOntology() == null)
            return;
        switch(msg.getOntology()){
            case "lookup":
                reply.setOntology("lookup");
                if (!isRegistered(sender)){
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("You are not registered");
                }
                else {
                    reply.setPerformative(ACLMessage.INFORM);
                    coordinates = stripMessage(msg.getContent());
                    tileValue = getCoordinate(coordinates[0], coordinates[1]);
                    if (tileValue >= 0)
                        reply.setContent("free");
                    else if (tileValue == -1)
                        reply.setContent("Occupied");
                    else if (tileValue == -2)
                        reply.setContent("off the track");
                    else
                        reply.setContent("out of bounds");
                }
                break;
            case "move":
                reply.setOntology("move");
                if (!isRegistered(sender)){
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("You are not registered");
                }
                else {
                    coordinates = stripMessage(msg.getContent());
                    tileValue = getCoordinate(coordinates[0], coordinates[1]);

                    if (tileValue < 0) {
                        reply.setPerformative(ACLMessage.FAILURE);
                        reply.setContent("Can't go there");
                    } else {
                        Competitor c = getCompetitorByName(sender.toString());
                        if (moveCompetitor(c, coordinates[0], coordinates[1]))
                            reply.setPerformative(ACLMessage.CONFIRM);
                        else
                            reply.setPerformative(ACLMessage.FAILURE);
                            reply.setContent(coordinates[0] +"," + coordinates[1]);
                    }
                }
                break;
            case "register":
                if (isRegistered(sender)) {
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("You are already registerd");
                }
                else{
                    int[] startPos = getNextStartPos();
                    if (startPos[0]==-1 || startPos[1] ==-1) {
                        reply.setPerformative(ACLMessage.FAILURE);
                        reply.setContent("No more free spaces");
                    }
                    else {
                        int startX = startPos[0];
                        int startY = startPos[1];
                        reply.setPerformative(ACLMessage.CONFIRM);
                        Competitor c = new Competitor(sender, startX, startY);
                        reply.setContent(startX + "," + startY);
                        competitors.add(c);
                        setCoordinate(startX, startY, -1);
                    }
                }
                break;
        }
        send(reply);
    }

    Competitor getCompetitorByName(String id)
    {
        Competitor result = null;
        for (int i = 0 ;i < competitors.size(); i++)
            if (competitors.get(i).getID().toString().equals(id))
                result = competitors.get(i);
        return result;
    }
    private boolean moveCompetitor(Competitor c, int x, int y)
    {
        if (getCoordinate(x, y) < 0)
            //can't got there
            return false;
        int curPos[] = c.getPos();
        int dist = abs(x-curPos[0]) + abs(y-curPos[1]);
        //myLogger.log(Level.SEVERE, abs(x-curPos[0])+" + " + abs(y-curPos[1]));
        if (dist > MAXSTEPS) {
            myLogger.log(Level.INFO, "NOPE:" + dist);
            return false;
        }

        //clear current pos on global map
        setCoordinate(curPos[0], curPos[1], 1);//TODO: fix

        //change internal pos of competitor
        c.setPos(x, y);
        //set new pos on global map
        setCoordinate(x, y, -1);
        return true;
    }

    //when a new competitor signs up, this is called to find out where he can start
    //TODO: gives position from anywhere on the map now, because there is no 'official' map yet.
    private int[] getNextStartPos() {
        int[] result = {-1, -1};
        for (int j = 0; j < mapHeight; j++)
            for (int i = 0; i < mapWidth; i++) {
                //System.out.println("DEBUG "+i+"," +j + ":" + getCoordinate(i, j));
                if (getCoordinate(i, j) >=0) {
                    result[0] = i;
                    result[1] = j;
                    return result;
                }
            }
        return result;
    }

    private int[] stripMessage(String msg){
       // System.out.println("DEBUG: " + msg);
        int x=0, y=0;
        try {
            int commaPos = msg.indexOf(',');
            String strX = msg.substring(0, commaPos);
            String strY = msg.substring(commaPos + 1, msg.length());
            //System.out.println("DEBUG: " + strX + "-"+ strY );
            if (!isInteger(strX) || !isInteger(strY)) {
                x = -1;
                y = -1;
            } else {
                x = Integer.valueOf(strX);
                y = Integer.valueOf(strY);
            }
        }
        catch(Exception e)
        {
            //ugly save
            myLogger.log(Level.SEVERE, "message is borked");
        }
        int result[] = {x, y};
        return result;
    }
    private boolean isRegistered(AID id) {
        for (Competitor c : competitors) {
            if (c.getID().toString().equals(id.toString()))
                return true;
        }
        return false;

    }

    //returns -1 if requested coordinate is out of bounds
    private int getCoordinate(int x, int y) {
        if (x > this.mapWidth  || y > this.mapHeight  || x < 0 || y < 0)
            return -2;
        return map[y][x];
    }
    private void setCoordinate(int x, int y, int newState) {
        if (x > this.mapWidth  || y > this.mapHeight  || x < 0 || y < 0)
            return;
        map[y][x] = newState;
    }

    //thank you Jonas Klemming
    private static boolean isInteger(String str) {
        if (str == null) {
            return false;
        }
        int length = str.length();
        if (length == 0) {
            return false;
        }
        int i = 0;
        if (str.charAt(0) == '-') {
            if (length == 1) {
                return false;
            }
            i = 1;
        }
        for (; i < length; i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }


}
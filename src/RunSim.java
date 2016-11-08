import java.util.Random;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.sql.*;

public class RunSim
{
	// allows for getting frame form elements' values
	public WSNFrame wsnFrame;	
	// array that holds all the cats
	public Cat[] cats;

	// mouse object
	public Mouse mouse = new Mouse();
	
	//ZACK
	int numSuccesses = 0;
	double totalMouseDistance = 0.0;
	
	//David
    public csvOut csv;
	
	// information about the simulation
	public int sensorCount1 = 50;
	public int sensorCount2 = 0;
	public float sensingRange1 = 20;
	public float sensingRange2 = 40;
	public float communicationRange = 999;
	public int detectionThreshold = 1;
	public float intruderSensingRange = 60;
	
	// panel size
	public int w = 1;
	public int h = 1;

	// the type of algorithm ran by the mouse
	public static String mouseAlgorithmType = "Linear";
	public static float mouseStart = 0;	
	public static String detectionAlgorithm = "";
	public static int maxIterations = 0;

	// information pertaining to how the simulation should be run
	public int numberOfTrials = 10;
	public int id;
		
	//  used for concurrent detection
	public int concurrentCaught = 0;
	public int[] caughtArray;


	public RunSim(int x, int y, WSNFrame frame)
	{
		String[] header =  {"Trial #", "Success", "Distance Traveled", "Successes", 
				"Total Distance", "Average Distance"};
		csv = new csvOut("output", header);		
		
		wsnFrame = frame;
		cats = new Cat[sensorCount1 + sensorCount2];
		mouse = new Mouse(0, mouseStart, mouseAlgorithmType, h, this);
		w = x;
		h = y;
		resetSurface();
	}

	public RunSim(int id, WSNFrame frame)
	{
		String[] header =  {"Trial #", "Success", "Distance Traveled", "Successes", 
				"Total Distance", "Average Distance"};
		csv = new csvOut("output", header);	
		
		wsnFrame = frame;
		// recallSimulation(id);
		cats = new Cat[sensorCount1 + sensorCount2];
		mouse = new Mouse(0, mouseStart, mouseAlgorithmType, h, this);
	}

	// Update-button from WSNFrame calls this
	public void updateSettings()
	{
		sensorCount1 = wsnFrame.getT1SensorCount();
		sensorCount2 = wsnFrame.getT2SensorCount();
		sensingRange1 = wsnFrame.getT1SensingRange();
		sensingRange2 = wsnFrame.getT2SensingRange();
		communicationRange = wsnFrame.getT1CommunicationRange();
		detectionThreshold = wsnFrame.getDetectionThreshold();
		mouseAlgorithmType = wsnFrame.getAlgorithmType();
		detectionAlgorithm = wsnFrame.getDetectionType();
		maxIterations = wsnFrame.getIterationsNum();
		w = wsnFrame.getFieldWidth();
		h = wsnFrame.getFieldHeight();
		
		// cats = new Cat[sensorCount];
		//resetSurface();
		restartSimulation(true);
	}

	public float findBiggestSensingRange()
	{
		if(sensingRange1 >= sensingRange2)
			return sensingRange1;
		else
			return sensingRange2;
	}
	
        // Reset sensor positions
	public void resetSurface()
	{
		Random rand = new Random();
		cats = new Cat[sensorCount1 + sensorCount2 + 1];
		cats[0] = new Cat(w,(int)h/2+1,true, 1);
		for(int i = 1; i <= sensorCount1; i++)
		{
			cats[i] = new Cat(w, h, sensingRange1);
			//cats[i] = new Cat(w, h, 15);
		}
		for(int i = sensorCount1 + 1; i <= sensorCount1 + sensorCount2; i++)
		{
			cats[i] = new Cat(w, h, sensingRange2);
			//cats[i] = new Cat(w, h, 30);
		}

		connectAllCats();

		concurrentCaught = 0;
		caughtArray = new int[detectionThreshold];
		for (int i=0; i<detectionThreshold; i++)
			caughtArray[i] = -1;
		
		mouseStart = rand.nextFloat()*h;
		mouse = new Mouse(-1*(2 + findBiggestSensingRange()), mouseStart, mouseAlgorithmType, h, this);
//		mouse = new Mouse(0, mouseStart, mouseAlgorithmType, h, this);
		mouse.resetPath();
		mouse.alg.resetAlg();
		//mouse.setAlgParameters(wsnFrame.getAlgorithmParameters());
	}

// restarts simulation with the same information
	private void restartSimulation(boolean surface_reset)
	{
		if(surface_reset)
			resetSurface();
		else
		{
			if(cats.length != sensorCount1 + sensorCount2)
			{
				resetSurface();
				return;
			}
			connectAllCats();		
			concurrentCaught = 0;
			caughtArray = new int[detectionThreshold];
			for (int i=0; i<detectionThreshold; i++)
				caughtArray[i] = -1;
			
//			mouse.setX(0);
			mouse.setX(-1*(2 + findBiggestSensingRange()));
			Random rand = new Random();
			mouseStart = rand.nextFloat()*h;
			mouse.setY(mouseStart);
			mouse.alg.resetAlg();
			mouse.resetPath();
		}
	}

// determines if the mouse is detected by the network
	private int detect(double mouseX, double mouseY)
	{
		for(int i = 0; i < cats.length; i++)
			if(cats[i].detect(mouseX, mouseY, cats[i].getSensingRange()) && cats[i].hasParent())
				return i;
		return -1;
	}

	//determines if the mouse is detected by three nodes
	private int concurrent(double mouseX, double mouseY)
	{
		if (concurrentCaught >= detectionThreshold)
			return caughtArray[concurrentCaught-1];
		
		for (int i=0; i<cats.length; i++)
		{
			if(cats[i].detect(mouseX, mouseY, cats[i].getSensingRange()) && cats[i].hasParent())
			{
				boolean used = false;
				for (int j=0; j<caughtArray.length; j++)
				{
					if (caughtArray[j] == i)
					{
						used = true;
						break;
					}
				}
				
				if (!used)
				{
					caughtArray[concurrentCaught++] = i;
					if (concurrentCaught >= detectionThreshold)
						return i;
				}
			}
		}
		return -1;
	}
		
// determines if the mouse is detected by n nodes at the same time		
	private int simultaneous(double mouseX, double mouseY)
	{
		int n = 0;
		
		for (int i=0; i<cats.length; i++)
		{
			if(cats[i].detect(mouseX, mouseY, cats[i].getSensingRange()) && cats[i].hasParent())
			{
				caughtArray[n++] = i;
				if(n >= detectionThreshold)
					return i;
			}
		}
		return -1;
	}


// connects all cats to the base node using the breadth first search algorithm
	private void connectAllCats()
	{
		// System.out.println(Arrays.toString(cats) + " "+ cats.length+ " " + sensorCount);
		//for (Cat n : cats)
			// n.distance = INFINITY
			//n.setParent(new Cat());
		for (int i = 1; i < sensorCount1 + sensorCount2; i++){
			cats[i].setParent(null);
		}

		// create empty queue Q
		// LinkedQueue<Cat> catQ = new LinkedQueue();
		ArrayDeque<Cat> catQ = new ArrayDeque();

		// v.distance = 0
		// catQ.enqueue(v);
		catQ.addFirst(cats[0]);
		Cat u;
		// while Q is not empty:
		while(!catQ.isEmpty())
		{
                    // u = Q.dequeue()
                    u = catQ.removeLast();
                    // for each node n that is adjacent to u:
                    // for(int i = 1; i < cats.length; i++)
                    for(int i = 1; i < sensorCount1 + sensorCount2; i++)
                    {
                                      
                        if( !cats[i].hasParent() &&
                            Math.abs(u.getX()-cats[i].getX()) < communicationRange &&
                            Math.abs(u.getY()-cats[i].getY()) < communicationRange &&
                            Math.sqrt(Math.pow((u.getX()-cats[i].getX()),2)+Math.pow((u.getY()-cats[i].getY()),2)) < communicationRange)
                            { 
                                // if cats[i].distance == INFINITY:
                                // cats[i].parent = u
                                cats[i].setParent(u);
                                // catQ.enqueue(n)
                                catQ.addFirst(cats[i]);
                            }
                        }
                }
	}

	public Cat[] getCats()
	{
		return cats;
	}

	public void createTable()
	{
		Connection c = null;
		Statement stmt = null;
		try 
		{
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:WSNSimulations.db");

			// querying goes here!
			stmt = c.createStatement();
			String sql = "CREATE TABLE IF NOT EXISTS sim_config ( "+
				 "id INTEGER PRIMARY KEY AUTOINCREMENT ,"+
				 "sensor_num INT NOT NULL ,"+
				 "detection_range INT NOT NULL ,"+
				 "sensor_move_alg VARCHAR(50) NULL ,"+
				 "intruder_move_alg VARCHAR(50) NULL ,"+
				 "communication_range INT NOT NULL ,"+
				 "detection_type VARCHAR(50) NOT NULL ,"+
				 "intruder_detect_range INT NULL ,"+
				 "animated BOOLEAN NULL ,"+
				 "times_ran INT NULL ,"+
				 "successes_num INT NULL ,"+
				 "field_height INT NOT NULL ,"+
				 "field_width INT NOT NULL ,"+
				 "intruder_size INT NULL ,"+
				 "intruder_start INT NULL ,"+
				 "max_iterations INT NULL ,"+
				 "threshold INT, "+
				 "base_sensor_loc INT NULL )";

			stmt.executeUpdate(sql);//works for everything but select statements
			stmt.close();

			sql = "CREATE TABLE IF NOT EXISTS `sim_sensors` ("+
				"`id`	INTEGER PRIMARY KEY AUTOINCREMENT,"+
				"`sim_id`	INTEGER NOT NULL,"+
				"`x`	INTEGER NOT NULL,"+
				"`y`	INTEGER NOT NULL"+
			");";
			stmt.executeUpdate(sql);
			stmt.close();

			sql = "CREATE TABLE IF NOT EXISTS `sim_trials` ("+
				"`id`	INTEGER PRIMARY KEY AUTOINCREMENT,"+
				"`sim_id`	INTEGER NOT NULL,"+
				"`success`	INTEGER NOT NULL,"+
				"`dist_linear`	INTEGER NOT NULL,"+
				"`dist_crawled`	INTEGER,"+
				"`time_alive`	INTEGER,"+
				"`sensor1_id`	INTEGER,"+
				"`sensor2_id`	INTEGER,"+
				"`sensor3_id`	INTEGER"+
			");";
			stmt.executeUpdate(sql);
			stmt.close();

			c.close();
		} 
		catch ( Exception e )
		{
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
		// System.out.println("Opened database successfully");
	}

// saves the current configuration of simulation to be used later	
	public void saveConfig()
	{
		Connection c = null;
		Statement stmt = null;
		try 
		{
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:WSNSimulations.db");

			stmt = c.createStatement();
			String sql = "INSERT INTO sim_config (sensor_num, detection_range, sensor_move_alg, " +
					"intruder_move_alg, communication_range, detection_type, intruder_detect_range, " + 
					"animated, times_ran, field_height, field_width, intruder_size, " +
					"intruder_start, max_iterations, threshold, base_sensor_loc) " +
					
					"VALUES ("+sensorCount1+", "+sensingRange1+", "+"NULL"+", '"+mouseAlgorithmType+"', "+
					communicationRange+", '"+detectionAlgorithm+"', "+(int)intruderSensingRange+", "+"NULL"+", "+"NULL"+", "+
					h+", "+w+", "+"NULL"+", "+"NULL"+", "+maxIterations+", "+detectionThreshold+", "+"NULL"+");";
			

			stmt.executeUpdate(sql);
			stmt.close();
			// c.close();
			// System.out.println("Did the first thing");
//			sql = "";
//			for(int i = 0; i < cats.length; i++)
//			{
//				// c = DriverManager.getConnection("jdbc:sqlite:WSNSimulations.db");
//				sql += "INSERT INTO sim_sensors (sim_id, x, y) VALUES("+wsnFrame.saving+", "+cats[i].getX()+", "+cats[i].getY()+");";	
//			}
//			stmt.executeUpdate(sql);
//			stmt.close();
			c.close();
		} 
		catch ( Exception e )
		{
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
		// System.out.println("Opened database successfully");
	}
	
	public String[] getConfigIds()
	{
		ArrayList<String> results = new ArrayList<String>();
		
		Connection c = null;
		Statement stmt = null;
		try 
		{
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:WSNSimulations.db");
			c.setAutoCommit(false);
			
			// querying goes here!
			stmt = c.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT id FROM sim_config;");
			while (rs.next())
				results.add( Integer.toString(rs.getInt("id")) );
			
			rs.close();
			stmt.close();
			c.close();
		} 
		catch ( Exception e )
		{
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
		// System.out.println("Opened database successfully");
		
		String[] r = new String[results.size()];
		r = results.toArray(r);
		return r;
	}
	
	public String[] getConfig(int id)
	{
		ArrayList<String> opts = new ArrayList<String>();
		
		Connection c = null;
		Statement stmt = null;
		try 
		{
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:WSNSimulations.db");
			c.setAutoCommit(false);
			
			// querying goes here!
			stmt = c.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT * FROM sim_config WHERE id == "+id+";");
			rs.next();
			
			opts.add( Integer.toString(rs.getInt("sensor_num")) );
			opts.add( Integer.toString(rs.getInt("detection_range")) );
			//opts.add( rs.getString("sensor_move_alg") );
			opts.add( rs.getString("intruder_move_alg") );
			opts.add( Integer.toString(rs.getInt("communication_range")) );
			opts.add( rs.getString("detection_type") );
			//opts.add( Integer.toString(rs.getInt("intruder_detect_range")) );
			//opts.add( Boolean.toString(rs.getBoolean("animated")) );
			//opts.add( Integer.toString(rs.getInt("times_ran")) );
			opts.add( Integer.toString(rs.getInt("field_height")) );
			opts.add( Integer.toString(rs.getInt("field_width")) );
			//opts.add( Integer.toString(rs.getInt("intruder_size")) );
			//opts.add( Integer.toString(rs.getInt("intruder_start")) );
			opts.add( Integer.toString(rs.getInt("max_iterations")) );
			opts.add( Integer.toString(rs.getInt("threshold")) );
			//opts.add( Integer.toString(rs.getInt("base_sensor_loc")) );
			
			rs.close();
			stmt.close();
//			 c.close();
		} 
		catch ( Exception e )
		{
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
		// System.out.println("Opened database successfully");
		
		String[] r = new String[opts.size()];
		r = opts.toArray(r);
		sensorCount1 = Integer.parseInt(r[0]);
		sensingRange1 = Integer.parseInt(r[1]);
		mouseAlgorithmType = r[2];
		communicationRange = Integer.parseInt(r[3]);
		detectionAlgorithm = r[4];
		h = Integer.parseInt(r[5]);
		w = Integer.parseInt(r[6]);
		maxIterations = Integer.parseInt(r[7]);
		detectionThreshold = Integer.parseInt(r[8]);
		
//		try
//		{
//			Class.forName("org.sqlite.JDBC");
//			c = DriverManager.getConnection("jdbc:sqlite:WSNSimulations.db");
//			c.setAutoCommit(false);
////			ResultSet rs = stmt.executeQuery("SELECT COUNT(sim_id) as count FROM sim_sensors WHERE sim_id="+id+";");
////			rs.next();
////			int totes = rs.getInt("count");
////			sensorCount = totes;
//			cats = new Cat[sensorCount];
//			ResultSet rs = stmt.executeQuery("SELECT * FROM sim_sensors WHERE sim_id="+id+";");
//			
//			for(int i = 0 ; i < sensorCount; i++)
//			{
//				rs.next();
//				cats[i] = new Cat(rs.getInt("x"),rs.getInt("y"),true);
//				// System.out.println(cats[i].getX()+", "+cats[i].getY());
//			}
//			rs.close();
//			stmt.close();
//			c.close();
//		}
//		catch(Exception e)
//		{
//			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
//			System.exit(0);
//		}
		wsnFrame.printToLog("Configuration #"+id+" has been loaded.");
		return r;
	}

//determines if the mouse is in a current state of success
	public boolean mouseSuccess()
	{
		return mouse.getX() >= w;
	}

// this is the main animation function for the panel
	public int doSimulationIteration()
	{
		// determines which cat the mouse is in the radius of
		int caught;
		switch(detectionAlgorithm)
		{
			case "Simultaneous":
				caught = simultaneous(mouse.getX(), mouse.getY());
				break;
			case "Concurrent":
				caught = concurrent(mouse.getX(), mouse.getY());
				break;
			default:
				caught = detect(mouse.getX(), mouse.getY());
		}
		
		// if the mouse is not detected by a cat, or detected by a cat not in the network, but it is also not at the end, then run this piece
		if(caught == -1 && mouse.getX() <= w)
		{
			// this only happens when the mouse isnt caught or succeeding
			mouse.moveNext();
		}
		return caught;
	}

// saves a trial of the current simulation
	public void saveTrial(int iteration)
	{
		//Zack shit
		if(iteration == 0)
		{
			numSuccesses = 0;
			totalMouseDistance = 0;
		}
		if(mouseSuccess())
			numSuccesses++;
		
		totalMouseDistance = totalMouseDistance + mouse.getX();
		
		//
		double mouseDist = mouse.getX();
/*Zack Shit*/	if(mouseDist < 0)
					mouseDist = 0; /**/
		int start = mouse.getTravels().get(0).y;
		wsnFrame.printToLog("Trial #"+iteration+": Success="+mouseSuccess()+"; Distance Traveled: "+mouseDist+"; Successes = " + numSuccesses + 
				"; Total Distance: " + totalMouseDistance + "; Average Distance: " + (totalMouseDistance / (iteration + 1)) + "\n");
		
		csv.append(iteration+", "+mouseSuccess()+", "+mouseDist+", " + numSuccesses + 
				", " + totalMouseDistance + ", " + (totalMouseDistance / (iteration + 1)));
		
		restartSimulation(true);
		// System.out.println("Saving Trial: "+w+" , "+mouseDist);
		if(wsnFrame.loaded == -1)
			return;
		Connection c = null;
		Statement stmt = null;
		try 
		{
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:WSNSimulations.db");

			stmt = c.createStatement();
			String sql = "INSERT INTO sim_trials ("+
				"sim_id,"+
				"success,"+
				"dist_linear ,"+
				"dist_crawled ,"+
				"time_alive ,"+
				"sensor1_id ,"+
				"sensor2_id ,"+
				"sensor3_id"+
			") " +
					
					"VALUES ("+
						wsnFrame.loaded+", "+
						(mouseDist >= w ? 1 : 0)+", "+
						mouseDist+", "+
						start+", "+
						"NULL, "+
						"NULL, "+
						"NULL, "+
						"NULL"+
						");";
			
			stmt.executeUpdate(sql);
			stmt.close();
			c.close();
		} 
		catch ( Exception e )
		{
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
		// System.out.println("Opened database successfully");
	}


// saves a trial of the current simulation
	public void saveTrial(int iteration, int saveNum)
	{
		double mouseDist = mouse.getX();
		wsnFrame.printToLog("Trial #"+iteration+": Success="+mouseSuccess()+"; Distance Traveled: "+mouseDist+";\n");
		restartSimulation(true);
		// System.out.println("Saving Trial: "+w+" , "+mouseDist);
		Connection c = null;
		Statement stmt = null;
		try 
		{
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:WSNSimulations.db");

			stmt = c.createStatement();
			String sql = "INSERT INTO sim_trials ("+
				"sim_id,"+
				"success,"+
				"dist_linear ,"+
				"dist_crawled ,"+
				"time_alive ,"+
				"sensor1_id ,"+
				"sensor2_id ,"+
				"sensor3_id"+
			") " +
					
					"VALUES ("+
						saveNum+", "+
						(mouseDist >= w ? 1 : 0)+", "+
						mouseDist+", "+
						"NULL, "+
						"NULL, "+
						"NULL, "+
						"NULL, "+
						"NULL"+
						");";
			
			stmt.executeUpdate(sql);
			// System.out.println("Save Worked");
			stmt.close();
			c.close();
		} 
		catch ( Exception e )
		{
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
		// System.out.println("Opened database successfully");
	}

	public void deleteConfig(int id)
	{
		if(wsnFrame.loaded == -1)
			return;
		Connection c = null;
		Statement stmt = null;
		try 
		{
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:WSNSimulations.db");

			stmt = c.createStatement();
			String sql = "DELETE FROM sim_config WHERE id="+id;
			
			stmt.executeUpdate(sql);
			stmt.close();
			sql = "DELETE FROM sim_sensors WHERE sim_id="+id;
			
			stmt.executeUpdate(sql);
			stmt.close();
			sql = "DELETE FROM sim_trials WHERE sim_id="+id;
			
			stmt.executeUpdate(sql);
			stmt.close();

			c.close();
		} 
		catch ( Exception e )
		{
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
		// System.out.println("Opened database successfully");
		wsnFrame.printToLog("Configuration #"+id+" has been deleted.\n");
	}

	public void createSuccessInfoTable()
	{
		Connection c = null;
		Statement stmt = null;
		try 
		{
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:WSNSimulations.db");

			stmt = c.createStatement();
			String sql = "CREATE TABLE IF NOT EXISTS SUCCESSES_INFO("+
			  "sensor_num INT,"+
			  "detection_range INT,"+
			  "communication_range INT,"+
			  "intruder_detect_range INT,"+
			  "success_prob"+
			")";
			stmt.executeUpdate(sql);
			c.close();
		}
		catch ( Exception e )
		{
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
	}
	
	public void insertIntoSuccessInfoTable(double prob)
	{
		Connection c = null;
		Statement stmt = null;
		try 
		{
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:WSNSimulations.db");

			stmt = c.createStatement();
			String sql = "INSERT INTO SUCCESSES_INFO ( sensor_num,"+
			  "detection_range,"+
			  "communication_range,"+
			  "intruder_detect_range,"+
			  "success_prob) VALUES ("+sensorCount1+","+sensingRange1+","+communicationRange+","+intruderSensingRange+","+prob+")";
			stmt.executeUpdate(sql);
			c.close();
		}
		catch ( Exception e )
		{
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
	}

//end of RunSim class
}
